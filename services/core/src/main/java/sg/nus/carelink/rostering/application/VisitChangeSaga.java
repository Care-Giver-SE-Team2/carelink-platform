package sg.nus.carelink.rostering.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.profile.application.RosteringProfiles;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;
import sg.nus.carelink.rostering.domain.model.VisitChangeRetries;
import sg.nus.carelink.rostering.domain.model.VisitChangeStep;
import sg.nus.carelink.rostering.domain.repository.RosterChangeAlert;
import sg.nus.carelink.rostering.domain.repository.VisitChangeStepRepository;
import sg.nus.carelink.rostering.domain.repository.VisitReassignment;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * The re-rostering saga: UC-MG04 decides in core's database and visit changes the visit in its own, and
 * this keeps the two in step, with core as the orchestrator. Each change is three parts:
 *
 * <ol>
 * <li><b>Decide</b> (core, one transaction): who takes the visit, the search behind it, the roster change
 * created or locked, and a {@link VisitChangeStep} for the change visit is to make, with its request key.
 * Nothing in it says the change is made, so if visit refuses, nothing has to be undone.</li>
 * <li><b>Make the change</b> (visit, its own transaction; the pivot): visit is asked once the decision has
 * committed, outside any transaction of core's, with the step's key. A refusal (409, 404) is final; no
 * answer - no connection, a read timeout, a 5xx, a 403 - may mean visit made the change or not, so the
 * step is sent again with the same key, and visit makes it once.</li>
 * <li><b>Settle</b> (core, one transaction): the roster change gets its outcome and people are told. Only
 * core's own data takes part, so it is retried until done; the step moves to DONE with one conditional
 * update, so it settles once when the request and the sweep finish the same step together.</li>
 * </ol>
 *
 * <p>The request that decided makes its changes before it answers, so when visit is well the caller sees
 * what it saw before the saga. A refusal of the caller's own change comes back to it as the error visit
 * gave (409 or 404); one that comes later goes to whoever decided as a notification. A step that cannot
 * get through is left to the sweep ({@link VisitChangeStepScanService}), and given up after
 * {@link VisitChangeRetries}: a manager gets an incident and the absence cannot be confirmed until the
 * manager re-rosters it, which sends the step again with its key.
 */
@Service
public class VisitChangeSaga {

	private static final Logger log = LoggerFactory.getLogger(VisitChangeSaga.class);

	/** English month names whatever the server's locale: these words end up in an incident a manager reads. */
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);

	/** visit's code for a change that lost to another at the same moment: not a refusal, so tried again. */
	private static final String CONCURRENT_UPDATE = "CONCURRENT_UPDATE";

	/** At most this many steps per sweep. */
	private static final int SWEEP_SIZE = 100;

	/** The steps the decision running on this thread has recorded, made once it commits. */
	private static final ThreadLocal<Deque<Long>> RECORDED = new ThreadLocal<>();

	private final VisitChangeStepRepository steps;
	private final VisitReassignment visits;
	private final IncidentService incidents;
	private final RosterChangeAlert alert;
	private final RosteringProfiles profiles;
	private final TransactionOperations transaction;
	private final VisitChangeRetries retries;
	private final Clock clock;

	@Autowired
	public VisitChangeSaga(VisitChangeStepRepository steps, VisitReassignment visits, IncidentService incidents,
			RosterChangeAlert alert, RosteringProfiles profiles, PlatformTransactionManager transactions, Clock clock) {
		this(steps, visits, incidents, alert, profiles, ownTransaction(transactions), VisitChangeRetries.DEFAULT,
				clock);
	}

	/** For tests: the transactions and the retries are the test's. */
	@SuppressWarnings("java:S107") // the use case's collaborators, plus the two a test replaces
	VisitChangeSaga(VisitChangeStepRepository steps, VisitReassignment visits, IncidentService incidents,
			RosterChangeAlert alert, RosteringProfiles profiles, TransactionOperations transaction,
			VisitChangeRetries retries, Clock clock) {
		this.steps = steps;
		this.visits = visits;
		this.incidents = incidents;
		this.alert = alert;
		this.profiles = profiles;
		this.transaction = transaction;
		this.retries = retries;
		this.clock = clock;
	}

	/**
	 * Every transaction here is a new one: a decision called from another transaction (the nightly
	 * recheck) commits on its own, and one called after another has committed (a roster refresh's
	 * listener) does not join a transaction that is over.
	 */
	private static TransactionOperations ownTransaction(PlatformTransactionManager transactions) {
		TransactionTemplate template = new TransactionTemplate(transactions);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return template;
	}

	/**
	 * The first part, then the rest: runs {@code decide} in a transaction of its own, and once that has
	 * committed makes each change it recorded and settles it with {@code settlement}, before returning
	 * what {@code decide} answered. A change visit cannot be reached for is left to the sweep, with the
	 * changes after it.
	 *
	 * @throws BusinessRuleViolation visit's refusal (409) of a change a person decided here
	 * @throws ResourceNotFound visit's answer (404) that the visit of such a change does not exist
	 */
	public <T> T decideThenChange(Supplier<T> decide, Settlement settlement) {
		if (RECORDED.get() != null) {
			// Decided inside another decision: its changes are made with that decision's
			return decide.get();
		}
		Deque<Long> recorded = new ArrayDeque<>();
		RECORDED.set(recorded);
		T decided;
		try {
			decided = transaction.execute(status -> decide.get());
		}
		finally {
			RECORDED.remove();
		}
		make(recorded, settlement, true);
		return decided;
	}

	/**
	 * Writes down a change for visit to make, in the deciding transaction. Inside
	 * {@link #decideThenChange} it is made as soon as the decision commits; the sweep makes it otherwise.
	 */
	VisitChangeStep record(Long absenceId, Long rosterChangeId, VacatedSlot visit, VisitChangeStep.Request request,
			VisitChangeStep.Decision decision) {
		LocalDateTime now = now();
		VisitChangeStep recorded = steps.add(VisitChangeStep.decided(absenceId, rosterChangeId, visit, request,
				decision, now.plus(retries.firstWait()), now));
		Deque<Long> batch = RECORDED.get();
		if (batch != null) {
			batch.add(recorded.id());
		}
		return recorded;
	}

	/** The absence's steps that still hold their visit, in the caller's transaction. */
	List<VisitChangeStep> holding(Long absenceId) {
		return steps.findHoldingByAbsenceId(absenceId);
	}

	/** The visits of an absence a step still holds: nothing else is decided for them. */
	Set<Long> heldVisits(Long absenceId) {
		return holding(absenceId).stream().map(step -> step.visit().visitId()).collect(Collectors.toSet());
	}

	/**
	 * A manager re-rosters the absence: every abandoned step is sent again, with its key and fresh
	 * retries, as part of the manager's decision.
	 *
	 * @return how many were sent again
	 */
	int sendAgain(Long absenceId) {
		LocalDateTime now = now();
		int sent = 0;
		for (VisitChangeStep step : holding(absenceId)) {
			if (step.isAbandoned()) {
				VisitChangeStep again = step.sentAgain(now.plus(retries.firstWait()), now);
				if (steps.advance(step, again)) {
					Deque<Long> batch = RECORDED.get();
					if (batch != null) {
						batch.add(again.id());
					}
					sent++;
				}
			}
		}
		return sent;
	}

	/** The steps due for another try, for the sweep. */
	List<Long> due() {
		List<VisitChangeStep> due = transaction.execute(status -> steps.findDue(now(), SWEEP_SIZE));
		return due == null ? List.of() : due.stream().map(VisitChangeStep::id).toList();
	}

	/**
	 * Raises the manager's incident for each step given up whose incident could not be raised then: raising
	 * one asks visit for the elder's latest caregiver, and visit may well be down still. For the sweep.
	 *
	 * @return how many incidents were raised
	 */
	int handOverGivenUp() {
		List<VisitChangeStep> unreported = transaction.execute(status -> steps.findGivenUpWithoutIncident(SWEEP_SIZE));
		int raised = 0;
		for (VisitChangeStep step : unreported == null ? List.<VisitChangeStep>of() : unreported) {
			if (handToManager(step)) {
				raised++;
			}
		}
		return raised;
	}

	/**
	 * Tries a step again, for the sweep, and makes whatever its settling leads to. Nobody is waiting for
	 * the answer, so a refusal is told to whoever decided the change.
	 */
	void tryAgain(Long stepId, Settlement settlement) {
		Deque<Long> queue = new ArrayDeque<>();
		queue.add(stepId);
		make(queue, settlement, false);
	}

	// ------------------------------------------------------------------ making the changes ---

	/**
	 * Makes the queued changes in turn; settling one may queue more (a moved visit offered again too late
	 * to ask goes to the default plan). Stops at the first that does not get through: visit is down, and
	 * the rest wait for the sweep rather than for one timeout each.
	 */
	private void make(Deque<Long> queue, Settlement settlement, boolean answering) {
		RuntimeException refusal = null;
		while (!queue.isEmpty()) {
			Try tried = makeOne(queue.poll(), settlement, queue, answering);
			if (refusal == null) {
				refusal = tried.refusal();
			}
			if (tried.result() == Result.NOT_THROUGH) {
				break;
			}
		}
		if (refusal != null) {
			throw refusal;
		}
	}

	private Try makeOne(Long stepId, Settlement settlement, Deque<Long> followUps, boolean answering) {
		VisitChangeStep step = transaction.execute(status -> steps.findById(stepId).orElse(null));
		if (step == null || !step.isPending()) {
			// Settled, refused or given up meanwhile, or rolled back with the decision that recorded it
			return Try.of(Result.GONE);
		}
		Long newVisitId;
		try {
			newVisitId = send(step);
		}
		catch (BusinessRuleViolation refused) {
			return CONCURRENT_UPDATE.equals(refused.code()) ? notThrough(step, refused) : refused(step, refused, answering);
		}
		catch (ResourceNotFound missing) {
			return refused(step, missing, answering);
		}
		catch (RuntimeException noAnswer) {
			return notThrough(step, noAnswer);
		}
		return settle(step, newVisitId, settlement, followUps);
	}

	/** The pivot: visit makes the change, or has made it already for this key. Outside any transaction. */
	private Long send(VisitChangeStep step) {
		VisitChangeStep.Request request = step.request();
		Long visitId = step.visit().visitId();
		VisitReassignment.Change why = new VisitReassignment.Change(step.absenceId(), request.byUserId(),
				request.rosteringCandidateId(), request.reason());
		return switch (request.kind()) {
			case REASSIGN -> {
				visits.reassign(visitId, request.toCaregiverId(), why, step.requestKey());
				yield null;
			}
			case MARK_UNCOVERED -> {
				visits.markUncovered(visitId, why, step.requestKey());
				yield null;
			}
			case CALL_OFF -> {
				visits.callOff(visitId, why, step.requestKey());
				yield null;
			}
			case MOVE -> visits.moveTo(visitId, request.newStart(), request.toCaregiverId(), why, step.requestKey());
		};
	}

	/** The third part: the step is DONE and its decision settled, both or neither. */
	private Try settle(VisitChangeStep step, Long newVisitId, Settlement settlement, Deque<Long> followUps) {
		try {
			transaction.executeWithoutResult(status -> {
				VisitChangeStep made = step.made(newVisitId, now());
				if (!steps.advance(step, made)) {
					// The request and the sweep both had visit make it; the other one settles it
					return;
				}
				Deque<Long> outer = RECORDED.get();
				RECORDED.set(followUps);
				try {
					Long changeId = settlement.settle(made);
					if (changeId != null && !changeId.equals(made.rosterChangeId())) {
						steps.advance(made, made.settling(changeId));
					}
				}
				finally {
					if (outer == null) {
						RECORDED.remove();
					}
					else {
						RECORDED.set(outer);
					}
				}
			});
			return Try.of(Result.DONE);
		}
		catch (RuntimeException failure) {
			// visit made the change and core could not record what follows: sent again with the same key,
			// visit answers as it did, and the decision is settled then
			log.error("Visit change step {} was made by visit but could not be settled", step.id(), failure);
			return notThrough(step, failure);
		}
	}

	/** Compensation: visit refused, so nothing was changed. The step lets go of the visit and says why. */
	private Try refused(VisitChangeStep step, RuntimeException refusal, boolean answering) {
		VisitChangeStep refusedStep = step.refused(describe(refusal), now());
		Long decidedBy = step.decision().decidedByUserId();
		Boolean moved = transaction.execute(status -> {
			if (!steps.advance(step, refusedStep)) {
				return false;
			}
			if (!answering && decidedBy != null) {
				alert.notMade(refusedStep, new RosterChangeAlert.Notice(elderName(step), null, null, null,
						refusal.getMessage()));
			}
			return true;
		});
		log.info("visit refused change step {} ({} of visit {}): {}", step.id(), step.request().kind(),
				step.visit().visitId(), refusal.getMessage());
		boolean theirs = Boolean.TRUE.equals(moved) && answering && decidedBy != null;
		return new Try(Result.REFUSED, theirs ? refusal : null);
	}

	/**
	 * No answer: the change may or may not be made, so the step is tried again with its key, or given up
	 * when the retries are spent or the visit is too close; a manager then gets an incident. The step is
	 * given up first, on its own: the incident may not be raised while visit is down, and the sweep raises
	 * it later ({@link #handOverGivenUp}).
	 */
	private Try notThrough(VisitChangeStep step, RuntimeException failure) {
		VisitChangeStep next = step.notThrough(describe(failure), now(), retries);
		Boolean moved = transaction.execute(status -> steps.advance(step, next));
		if (Boolean.TRUE.equals(moved) && next.isAbandoned()) {
			handToManager(next);
		}
		if (failure instanceof AccessDeniedException) {
			log.error("visit refused core's call for change step {}: check the internal API's configuration",
					step.id(), failure);
		}
		else {
			log.warn("visit did not answer change step {} (try {}, {}): {}", step.id(), next.attempts(),
					next.isAbandoned() ? "given up" : "again at " + next.nextTryAt(), failure.getMessage());
		}
		return Try.of(Result.NOT_THROUGH);
	}

	/**
	 * The incident for a step given up, raised with the step's note of it, both or neither. A step sent
	 * again meanwhile gets none.
	 *
	 * @return whether the incident was raised
	 */
	private boolean handToManager(VisitChangeStep abandoned) {
		try {
			return Boolean.TRUE.equals(transaction.execute(status -> {
				Long incident = incidents.raiseForUnrosteredLeaveVisit(abandoned.visit().elderId(),
						abandoned.visit().visitId(), abandoned(abandoned)).id();
				if (steps.advance(abandoned, abandoned.handedToManager(incident))) {
					return true;
				}
				status.setRollbackOnly();
				return false;
			}));
		}
		catch (RuntimeException failure) {
			log.warn("Visit change step {} is given up and its incident could not be raised yet; the next sweep"
					+ " tries again: {}", abandoned.id(), failure.getMessage());
			return false;
		}
	}

	private String abandoned(VisitChangeStep step) {
		VacatedSlot visit = step.visit();
		return ("%s visit at %s: the visit service could not be reached to %s after %d tries (absence %d)."
				+ " Check the visit, then re-roster the absence to send the change again").formatted(
						visit.serviceType() == null ? "A" : visit.serviceType(), visit.start().format(WHEN),
						verb(step.request().kind()), step.attempts(), step.absenceId());
	}

	private static String verb(VisitChangeStep.Kind kind) {
		return switch (kind) {
			case REASSIGN -> "hand it over";
			case MARK_UNCOVERED -> "leave it uncovered";
			case CALL_OFF -> "call it off";
			case MOVE -> "move it";
		};
	}

	private String elderName(VisitChangeStep step) {
		return profiles.elder(step.visit().elderId()).map(RosteringProfiles.ElderFacts::fullName).orElse(null);
	}

	private static String describe(RuntimeException failure) {
		String code = failure instanceof BusinessRuleViolation rule ? rule.code() : failure.getClass().getSimpleName();
		return failure.getMessage() == null ? code : code + ": " + failure.getMessage();
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	/** What core does once visit has made a step's change: the saga's third part. */
	@FunctionalInterface
	public interface Settlement {

		/**
		 * Settles the step's decision. Runs once per step, in the transaction that marks it done.
		 *
		 * @return the roster change the step settled
		 */
		Long settle(VisitChangeStep made);
	}

	private enum Result {
		DONE, REFUSED, NOT_THROUGH, GONE
	}

	/** How one try went, and the refusal to give the caller who decided, if there is one. */
	private record Try(Result result, RuntimeException refusal) {

		static Try of(Result result) {
			return new Try(result, null);
		}
	}
}
