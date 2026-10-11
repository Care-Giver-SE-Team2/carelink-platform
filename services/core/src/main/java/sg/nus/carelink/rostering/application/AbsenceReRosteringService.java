package sg.nus.carelink.rostering.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.RosteringProfiles;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.FamilyResponseWindow;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.model.RosteringCandidate;
import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;
import sg.nus.carelink.rostering.domain.model.VisitChangeStep;
import sg.nus.carelink.rostering.domain.repository.AbsenceReportRepository;
import sg.nus.carelink.rostering.domain.repository.RosterAudit;
import sg.nus.carelink.rostering.domain.repository.RosterChangeAlert;
import sg.nus.carelink.rostering.domain.repository.RosterChangeRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringCandidateCheckRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringCandidateRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringConstraintRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringRunRepository;
import sg.nus.carelink.rostering.domain.service.Booking;
import sg.nus.carelink.rostering.domain.service.ReplacementFinder;
import sg.nus.carelink.rostering.domain.service.RosterSnapshot;
import sg.nus.carelink.rostering.domain.service.ScoringObjective;
import sg.nus.carelink.rostering.domain.service.Shortlist;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.rostering.domain.repository.VisitReassignment;

/**
 * UC-MG04 re-roster on caregiver absence, from the manager's "re-roster now" to the family's
 * answer, the default plan when the answer does not come, and the manager's confirmation.
 *
 * <p>Each public method is one step and does the same few things: load, ask the domain,
 * change the visit through the visit module, save, tell people. Who may go and in what order is
 * {@link ReplacementFinder}'s business; whether a change may still be settled is
 * {@link RosterChange}'s. Visits are only ever changed through {@link VisitReassignment}, never
 * here (DECISION 17).
 *
 * <p>Every way a visit can end up is written to its roster_change, which is how the use case's
 * first rule is kept: no vacated visit disappears without a record.
 *
 * <p>visit is another service, so a step cannot change a visit and core's records in one transaction.
 * Each public method is the re-rostering saga ({@link VisitChangeSaga}): it decides in a transaction of
 * its own and writes down each change visit is to make; once that has committed, visit is asked, and only
 * when visit has made a change is the roster change settled and anybody told ({@link #settle}). Until
 * then the step holds its visit, and nothing else is decided for it (the saga's semantic lock). When
 * visit is well, all of this happens before the method returns, as it did when visit was a module.
 */
@Service
public class AbsenceReRosteringService {

	/** How many suggestions the family is offered to choose between. */
	public static final int FAMILY_OPTIONS = 3;

	/** English month names whatever the server's locale: these words end up in notes families read. */
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);

	private final AbsenceReportRepository absences;
	private final RosterChangeRepository changes;
	private final RosteringRunRepository runs;
	private final RosteringCandidateRepository candidates;
	private final ReplacementSearchRecord search;
	private final RosterSnapshotLoader snapshots;
	private final VisitReassignment visits;
	private final RosteringProfiles profiles;
	private final IncidentService incidents;
	private final FamilyAccessQuery familyAccess;
	private final UserDirectory users;
	private final RosterChangeAlert alert;
	private final RosterAudit audit;
	private final FamilyResponseWindow window;
	private final VisitChangeSaga saga;
	private final Clock clock;

	@SuppressWarnings("java:S107") // one collaborator per module this use case reaches; a facade would only hide that
	public AbsenceReRosteringService(AbsenceReportRepository absences, RosterChangeRepository changes,
			RosteringRunRepository runs, RosteringCandidateRepository candidates,
			RosteringCandidateCheckRepository checks, RosteringConstraintRepository constraints,
			RosterSnapshotLoader snapshots, VisitReassignment visits, RosteringProfiles profiles,
			IncidentService incidents, FamilyAccessQuery familyAccess, UserDirectory users, RosterChangeAlert alert,
			RosterAudit audit, FamilyResponseWindow window, VisitChangeSaga saga, Clock clock) {
		this.absences = absences;
		this.changes = changes;
		this.runs = runs;
		this.candidates = candidates;
		this.search = new ReplacementSearchRecord(constraints, candidates, checks);
		this.snapshots = snapshots;
		this.visits = visits;
		this.profiles = profiles;
		this.incidents = incidents;
		this.familyAccess = familyAccess;
		this.users = users;
		this.alert = alert;
		this.audit = audit;
		this.window = window;
		this.saga = saga;
		this.clock = clock;
	}

	// ------------------------------------------------------------------ steps 2-4 ---

	/**
	 * Steps 2 to 4: the manager re-rosters an approved absence. Every visit it vacates that has
	 * no change yet is searched; a visit somebody can take is offered to the family, or settled
	 * by the default plan at once when it starts too soon to ask; a visit nobody can take is
	 * left uncovered with an incident raised. Visits left uncovered by an earlier run are
	 * searched again, and taken straight away when somebody has become free.
	 *
	 * <p>Safe to repeat: a visit already offered or settled is not touched again, which is also
	 * how visits the nightly roster adds to the absence's days later get picked up.
	 *
	 * <p>A manager's re-roster also sends again every change of the absence visit could not be reached
	 * for in time (an abandoned saga step), with the key it was first sent with.
	 *
	 * @param managerUserId null when the nightly run re-rosters, not a manager
	 *     ({@link AbsenceCoverageService}); an uncovered visit it takes up is then settled by the
	 *     default plan rather than in a manager's name
	 */
	public ReRosterOutcome reroster(Long absenceId, RosteringRun.Objective objective, Long managerUserId) {
		return saga.decideThenChange(() -> rerosterDecision(absenceId, objective, managerUserId), this::settle);
	}

	private ReRosterOutcome rerosterDecision(Long absenceId, RosteringRun.Objective objective, Long managerUserId) {
		AbsenceReport absence = requireApproved(absenceId);
		ScoringObjective scoring = ScoringObjective.of(objective);
		LocalDateTime now = now();
		if (managerUserId != null) {
			saga.sendAgain(absenceId);
		}
		Map<Long, RosterChange> byVisit = changesByVisit(absenceId);
		Set<Long> held = saga.heldVisits(absenceId);
		List<VacatedSlot> slots = new ArrayList<>(unhandled(absence, byVisit, held, now));
		slots.addAll(uncoveredToRetry(absence, byVisit.values(), held, now));
		slots.sort(Comparator.comparing(VacatedSlot::start));
		if (slots.isEmpty()) {
			return new ReRosterOutcome(absenceId, null, 0, 0, 0, 0);
		}

		ReplacementSearchRecord.RuleSet rules = search.ruleSet();
		ReplacementFinder finder = new ReplacementFinder(rules.rules(), scoring);
		RosterSnapshot snapshot = snapshots.load(slots);
		RosteringRun run = runs.save(RosteringRun.forAbsence(absenceId, finder.objective().objective(),
				managerUserId, now));

		int offered = 0;
		int settled = 0;
		int uncovered = 0;
		int continuity = 0;
		for (VacatedSlot slot : slots) {
			Shortlist shortlist = finder.shortlist(slot, snapshot);
			Map<Long, Long> candidateIds = search.record(run.id(), shortlist, rules);
			RosterChange existing = byVisit.get(slot.visitId());
			Optional<Shortlist.Verdict> best = shortlist.best();

			if (best.isEmpty()) {
				if (existing == null) {
					uncover(absenceId, slot, run.id(), null, "Nobody was free to cover this visit", managerUserId);
				}
				else {
					changes.save(existing.searchedAgain(run.id(), now));
				}
				uncovered++;
				continue;
			}

			snapshot.reserve(Booking.of(slot, best.get().caregiverId()));
			if (snapshot.elder(slot.elderId()).priorVisitsBy(best.get().caregiverId()) > 0) {
				continuity++;
			}
			if (existing != null) {
				boolean bySystem = managerUserId == null;
				replace(existing, slot, best.get(), run.id(), candidateIds,
						bySystem ? RosterChange.DecidedBy.DEFAULT_PLAN : RosterChange.DecidedBy.MANAGER, managerUserId,
						bySystem
								? "Somebody became free for the uncovered visit, so the default plan put them on it"
								: "A manager re-rostered the uncovered visit once somebody was free");
				settled++;
				continue;
			}

			Optional<LocalDateTime> respondBy = window.respondBy(now, slot.start());
			RosterChange change = changes.save(RosterChange.offered(absenceId, slot, run.id(),
					best.get().caregiverId(), respondBy.orElse(now), now));
			if (respondBy.isPresent()) {
				alert.offered(change, notice(change, best.get().name(), best.get().caregiverId(), null, null));
				offered++;
			}
			else {
				replace(change, slot, best.get(), run.id(), candidateIds, RosterChange.DecidedBy.DEFAULT_PLAN, null,
						"The visit starts too soon to ask the family first, so the best replacement was put on it");
				settled++;
			}
		}

		runs.save(run.committed(slots.size(), slots.size() - uncovered, continuity, now));
		return new ReRosterOutcome(absenceId, run.id(), slots.size(), offered, settled, uncovered);
	}

	// ------------------------------------------------------------------ steps 5-6 ---

	/**
	 * Steps 5 and 6: the family answers, and the roster is rewritten their way. A family may
	 * only answer for an elder they are bound to, and only while their time is not up; after
	 * that the default plan is already due and a late answer would race it. Not while an earlier
	 * change to the visit is still being made.
	 *
	 * @return the change as it stands once visit has been asked: settled, or still waiting for visit
	 */
	public RosterChange decide(Long changeId, String familyUsername, FamilyChoice choice) {
		saga.decideThenChange(() -> familyDecision(changeId, familyUsername, choice), this::settle);
		return current(changeId);
	}

	private RosterChange familyDecision(Long changeId, String familyUsername, FamilyChoice choice) {
		RosterChange change = changes.lock(changeId).orElseThrow(() -> new ResourceNotFound("RosterChange", changeId));
		familyAccess.requireReadableElder(familyUsername, change.elderId());
		requireNoChangeUnderWay(change);
		Long familyUserId = users.findByUsername(familyUsername).map(AppUser::id).orElse(null);

		if (!change.awaitingFamily()) {
			throw new BusinessRuleViolation("ROSTER_CHANGE_NOT_OPEN",
					"This visit has already been settled: " + describe(change));
		}
		if (!change.familyMayAnswer(now())) {
			throw new BusinessRuleViolation("FAMILY_WINDOW_CLOSED",
					"The time to choose ended at %s; the institution's default plan applies"
							.formatted(change.respondBy().format(WHEN)));
		}
		return switch (choice.kind()) {
			case CHANGE_CAREGIVER -> pick(change, choice.caregiverId(), familyUserId);
			case RESCHEDULE -> reschedule(change, choice.newStart(), familyUserId);
			case SKIP -> skip(change, familyUserId);
		};
	}

	// ------------------------------------------------------------------ alternative 4a ---

	/**
	 * Alternative 4a: the family did not answer in time, so the institution's default plan -
	 * the best replacement - runs, and is logged as the default plan rather than a choice.
	 *
	 * <p>Its own transaction, called once per change by the scan, so one change that cannot be
	 * settled does not hold back the others. The change is locked and checked again first: the
	 * family may have answered between the scan's read and this call. A change whose visit an earlier
	 * change is still being made to is left for a later sweep.
	 *
	 * @return whether the default plan ran
	 */
	public boolean applyDefaultIfStillDue(Long changeId) {
		return Boolean.TRUE.equals(saga.decideThenChange(() -> defaultPlanDecision(changeId), this::settle));
	}

	private boolean defaultPlanDecision(Long changeId) {
		Optional<RosterChange> locked = changes.lock(changeId);
		if (locked.isEmpty() || !locked.get().defaultPlanDue(now())
				|| saga.heldVisits(locked.get().absenceId()).contains(locked.get().visitId())) {
			return false;
		}
		RosterChange change = locked.get();
		settleWithRecheck(change, change.proposedCaregiverId(), RosterChange.DecidedBy.DEFAULT_PLAN, null,
				"No answer from the family by %s, so the default plan ran".formatted(change.respondBy().format(WHEN)));
		return true;
	}

	// ------------------------------------------------------------------ step 7 ---

	/**
	 * Step 7: the manager confirms every visit the absence vacated is accounted for - given to
	 * somebody, moved, skipped, or uncovered with an incident somebody answers for. Not while a
	 * family is still deciding, not while a change to a visit is still being made or has been given up
	 * (a re-roster sends it again), and not while visits are left nobody has re-rostered.
	 */
	public AbsenceReport confirmCoverage(Long absenceId, Long managerUserId) {
		return saga.decideThenChange(() -> confirmation(absenceId, managerUserId), this::settle);
	}

	private AbsenceReport confirmation(Long absenceId, Long managerUserId) {
		AbsenceReport absence = requireApproved(absenceId);
		Map<Long, RosterChange> byVisit = changesByVisit(absenceId);
		long deciding = byVisit.values().stream().filter(RosterChange::awaitingFamily).count();
		if (deciding > 0) {
			throw new BusinessRuleViolation("FAMILY_STILL_DECIDING",
					"%d visit%s still waiting for the family's answer".formatted(deciding, deciding == 1 ? " is" : "s are"));
		}
		List<VisitChangeStep> held = saga.holding(absenceId);
		if (!held.isEmpty()) {
			throw changesUnderWay(held);
		}
		int notYet = unhandled(absence, byVisit, Set.of(), now()).size();
		if (notYet > 0) {
			throw new BusinessRuleViolation("VISITS_NOT_REROSTERED",
					"%d visit%s the absence vacates %s not been re-rostered yet; re-roster first"
							.formatted(notYet, notYet == 1 ? "" : "s", notYet == 1 ? "has" : "have"));
		}
		return absences.save(absence.coverageConfirmedBy(managerUserId, now()));
	}

	// ------------------------------------------------------------------ the manager's own pick ---

	/**
	 * The manager puts a caregiver of their choosing on a vacated visit instead of whoever the
	 * search ranked first: when nobody was free, or over the institution's own pick, never over
	 * the family's ({@link RosterChange#managerMayAssign()}). The caregiver must still pass every
	 * hard rule against the roster as it is now; if not, nothing changes and the manager is told
	 * which rule stopped them. The family is told who is coming. Not while an earlier change to the visit
	 * is still being made.
	 *
	 * @return the change as it stands once visit has been asked
	 */
	public RosterChange assignByManager(Long absenceId, Long changeId, Long caregiverId, Long managerUserId) {
		saga.decideThenChange(() -> managerDecision(absenceId, changeId, caregiverId, managerUserId), this::settle);
		return current(changeId);
	}

	private RosterChange managerDecision(Long absenceId, Long changeId, Long caregiverId, Long managerUserId) {
		RosterChange change = changes.lock(changeId)
				.filter(c -> c.absenceId().equals(absenceId))
				.orElseThrow(() -> new ResourceNotFound("RosterChange", changeId));
		requireNoChangeUnderWay(change);
		change.requireManagerMayAssign(caregiverId);
		LocalDateTime now = now();
		VisitReassignment.VisitSlot visit = visits.find(change.visitId())
				.filter(v -> "SCHEDULED".equals(v.status()) || "EXCEPTION".equals(v.status()) && v.caregiverId() == null)
				.filter(v -> v.start().isAfter(now))
				.orElseThrow(() -> new BusinessRuleViolation("VISIT_NOT_OPEN",
						"The visit has started, passed or been called off, so it can no longer be reassigned"));

		VacatedSlot slot = toSlot(visit, change.originalCaregiverId());
		ReplacementSearchRecord.RuleSet rules = search.ruleSet();
		ReplacementFinder finder = new ReplacementFinder(rules.rules(), ScoringObjective.of(objectiveOf(change)));
		RosterSnapshot snapshot = snapshots.load(List.of(slot));
		Shortlist shortlist = finder.shortlist(slot, snapshot);
		Shortlist.Verdict chosen = shortlist.all().stream()
				.filter(verdict -> verdict.caregiverId().equals(caregiverId))
				.findFirst()
				.orElseThrow(() -> new ResourceNotFound("Caregiver", caregiverId));
		if (!chosen.isSuggested()) {
			throw new BusinessRuleViolation("CAREGIVER_CANNOT_TAKE_VISIT",
					"%s cannot take this visit: %s".formatted(chosen.name(), chosen.reason()));
		}

		RosteringRun run = runs.save(RosteringRun.forAbsence(change.absenceId(), finder.objective().objective(),
				managerUserId, now));
		Map<Long, Long> candidateIds = search.record(run.id(), shortlist, rules);
		runs.save(run.committed(1, 1, snapshot.elder(slot.elderId()).priorVisitsBy(caregiverId) > 0 ? 1 : 0, now));
		return replace(change, slot, chosen, run.id(), candidateIds, RosterChange.DecidedBy.MANAGER, managerUserId,
				"A manager chose %s for the visit".formatted(chosen.name()));
	}

	// ------------------------------------------------------------------ the family's three options ---

	/** Keep the suggestion, or pick another of the options the family was shown. */
	private RosterChange pick(RosterChange change, Long caregiverId, Long familyUserId) {
		Long wanted = caregiverId == null ? change.proposedCaregiverId() : caregiverId;
		boolean offered = candidates.findByRunAndVisit(change.rosteringRunId(), change.visitId()).stream()
				.filter(RosteringCandidate::isSuggestion)
				.filter(c -> c.optionRank() != null && c.optionRank() <= FAMILY_OPTIONS)
				.anyMatch(c -> c.caregiverId().equals(wanted));
		if (!offered) {
			throw new BusinessRuleViolation("NOT_ONE_OF_THE_OPTIONS",
					"Caregiver %d was not one of the options offered for this visit".formatted(wanted));
		}
		return settleWithRecheck(change, wanted, RosterChange.DecidedBy.FAMILY, familyUserId,
				wanted.equals(change.proposedCaregiverId())
						? "The family kept the suggested replacement"
						: "The family chose another of the suggestions");
	}

	/**
	 * Alternative 4b: move the visit, then back to step 3 for the new time. The search runs again
	 * for it - the absent caregiver is a candidate again if the new time is after their leave -
	 * and the visit moves there with the best person free then pencilled in. The family is then
	 * offered the choice for the new time, under the same window and default plan as the first:
	 * keep that person, pick another of the suggestions, move it again or skip it. Nobody free
	 * means the family is asked for another time; nothing changes.
	 *
	 * <p>Decided here, made by visit and settled in {@link #moved}: the search is filed under the visit
	 * as it is, and under the visit that replaces it once visit has made that one.
	 */
	private RosterChange reschedule(RosterChange change, LocalDateTime newStart, Long familyUserId) {
		LocalDateTime now = now();
		if (newStart == null) {
			throw new BusinessRuleViolation("NEW_TIME_REQUIRED", "Moving a visit needs the time to move it to");
		}
		if (!newStart.isAfter(now.plus(window.lead()))) {
			throw new BusinessRuleViolation("NEW_TIME_TOO_SOON",
					"Pick a time at least %d minutes from now, so whoever takes it can get there"
							.formatted(window.lead().toMinutes()));
		}
		VacatedSlot current = openSlot(change);
		VacatedSlot moved = current.movedTo(newStart);
		RosterSnapshot snapshot = snapshots.load(List.of(moved));
		snapshot.elderClash(moved.elderId(), moved.start(), moved.effectiveEnd(), moved.visitId())
				.ifPresent(other -> {
					throw new BusinessRuleViolation("ELDER_BUSY_THEN",
							"The elder already has a visit at %s".formatted(other.start().format(WHEN)));
				});

		ReplacementSearchRecord.RuleSet rules = search.ruleSet();
		ReplacementFinder finder = new ReplacementFinder(rules.rules(), ScoringObjective.of(objectiveOf(change)));
		Shortlist shortlist = finder.shortlist(moved, snapshot);
		Shortlist.Verdict best = shortlist.best().orElseThrow(() -> new BusinessRuleViolation("NOBODY_FREE_THEN",
				"Nobody is free at %s; try another time, or another option".formatted(newStart.format(WHEN))));

		RosteringRun run = runs.save(RosteringRun.forAbsence(change.absenceId(), finder.objective().objective(),
				familyUserId, now));
		search.record(run.id(), shortlist, rules);
		runs.save(run.committed(1, 1, snapshot.elder(moved.elderId()).priorVisitsBy(best.caregiverId()) > 0 ? 1 : 0, now));
		saga.record(change.absenceId(), change.id(), current,
				VisitChangeStep.Request.move(newStart, best.caregiverId(), familyUserId,
						"Moved at the family's request while the caregiver is absent (absence %d); the family chooses who comes"
								.formatted(change.absenceId())),
				new VisitChangeStep.Decision(run.id(), RosterChange.DecidedBy.FAMILY, familyUserId, best.name(),
						"Moved to %s at the family's request".formatted(newStart.format(WHEN))));
		return change;
	}

	/**
	 * Step 3 again, for the visit at its new time: the family is offered the suggestions the search
	 * just made, with the pencilled-in person as the default. The offer is a change of its own, on
	 * the new visit, so it is answered, defaulted and confirmed like every other.
	 */
	private void offerTheNewTime(RosterChange moved, Long newVisitId, Long runId, Long caregiverId,
			String caregiverName, LocalDateTime now) {
		VacatedSlot slot = toSlot(visits.find(newVisitId).orElseThrow(), moved.originalCaregiverId());
		Optional<LocalDateTime> respondBy = window.respondBy(now, slot.start());
		RosterChange offer = changes.save(RosterChange.offered(moved.absenceId(), slot, runId, caregiverId,
				respondBy.orElse(now), now));
		if (respondBy.isPresent()) {
			alert.offered(offer, notice(offer, caregiverName, caregiverId, null, null));
		}
		else {
			// A step of its own, which the saga makes after this one
			settleWithRecheck(offer, caregiverId, RosterChange.DecidedBy.DEFAULT_PLAN, null,
					"The new time is too soon to ask the family again, so the best replacement keeps the visit");
		}
	}

	/**
	 * Alternative 4c: skip this visit. Called off, recorded as the family's own cancellation once visit
	 * has called it off ({@link #skipped}).
	 */
	private RosterChange skip(RosterChange change, Long familyUserId) {
		VacatedSlot slot = openSlot(change);
		String why = "Skipped at the family's request";
		// May be skipped: the check, before visit is asked
		change.skipped(familyUserId, why, now());
		saga.record(change.absenceId(), change.id(), slot, VisitChangeStep.Request.callOff(familyUserId,
				"Skipped at the family's request while the caregiver is absent (absence " + change.absenceId() + ")"),
				new VisitChangeStep.Decision(change.rosteringRunId(), RosterChange.DecidedBy.FAMILY, familyUserId, null,
						why));
		return change;
	}

	// ------------------------------------------------------------------ settling ---

	/**
	 * Puts {@code wanted} on the visit if they are still free, checked against the roster as it
	 * is now rather than as it was when the offer went out (exception 5a). If they are not, the
	 * best of whoever is takes it, and if nobody is, the visit is left uncovered with an incident.
	 * Each check is a run of its own, so both the offer and what finally happened can be traced.
	 */
	private RosterChange settleWithRecheck(RosterChange change, Long wanted, RosterChange.DecidedBy by, Long userId,
			String why) {
		Optional<VisitReassignment.VisitSlot> current = visits.find(change.visitId());
		if (current.isEmpty() || !"SCHEDULED".equals(current.get().status())) {
			return changes.save(change.withdrawn("The visit was %s before the change was settled"
					.formatted(current.map(v -> v.status().toLowerCase()).orElse("removed")), now()));
		}
		VacatedSlot slot = toSlot(current.get(), change.originalCaregiverId());
		ReplacementSearchRecord.RuleSet rules = search.ruleSet();
		ReplacementFinder finder = new ReplacementFinder(rules.rules(), ScoringObjective.of(objectiveOf(change)));
		RosterSnapshot snapshot = snapshots.load(List.of(slot));
		Shortlist shortlist = finder.shortlist(slot, snapshot);
		LocalDateTime now = now();
		RosteringRun run = runs.save(RosteringRun.forAbsence(change.absenceId(), finder.objective().objective(),
				userId, now));
		Map<Long, Long> candidateIds = search.record(run.id(), shortlist, rules);

		Optional<Shortlist.Verdict> chosen = shortlist.ranked().stream()
				.filter(verdict -> verdict.caregiverId().equals(wanted))
				.findFirst()
				.or(shortlist::best);
		runs.save(run.committed(1, chosen.isPresent() ? 1 : 0,
				chosen.filter(v -> snapshot.elder(slot.elderId()).priorVisitsBy(v.caregiverId()) > 0).isPresent() ? 1 : 0,
				now));
		if (chosen.isEmpty()) {
			return uncover(change.absenceId(), slot, run.id(), change,
					"%s, but nobody was free any more, so the visit is uncovered".formatted(why), userId);
		}
		String note = chosen.get().caregiverId().equals(wanted)
				? why
				: "%s; %s was no longer free, so %s took the visit".formatted(why, nameOf(wanted), chosen.get().name());
		return replace(change, slot, chosen.get(), run.id(), candidateIds, by, userId, note);
	}

	/**
	 * The visit goes to {@code verdict}'s caregiver: decided here, made by visit, and settled in
	 * {@link #replaced}, where everybody is told and a default plan is audited.
	 */
	private RosterChange replace(RosterChange change, VacatedSlot slot, Shortlist.Verdict verdict, Long runId,
			Map<Long, Long> candidateIds, RosterChange.DecidedBy by, Long userId, String why) {
		Long candidateId = candidateIds.get(verdict.caregiverId());
		// May be given to them: the check, before visit is asked
		change.replacedBy(verdict.caregiverId(), runId, by, userId, why, now());
		saga.record(change.absenceId(), change.id(), slot, VisitChangeStep.Request.reassign(verdict.caregiverId(),
				userId, candidateId, "%s (absence %d): %s".formatted(byWhom(by), change.absenceId(), why)),
				new VisitChangeStep.Decision(runId, by, userId, verdict.name(), why));
		return change;
	}

	/**
	 * Exception 3a: nobody can take the visit. Decided here; once visit has made it an exception,
	 * {@link #leftUncovered} gives it an incident with a named responder, and the family hears the
	 * institution is on it.
	 *
	 * @param existing the change the visit already has; null for a visit re-rostered for the first time,
	 *     whose change is recorded once visit has made the visit an exception
	 * @param userId who decided; null when the system did
	 */
	private RosterChange uncover(Long absenceId, VacatedSlot slot, Long runId, RosterChange existing, String why,
			Long userId) {
		saga.record(absenceId, existing == null ? null : existing.id(), slot,
				VisitChangeStep.Request.markUncovered(why), new VisitChangeStep.Decision(runId, null, userId, null, why));
		return existing;
	}

	// ------------------------------------------------------------------ settling, once visit has made the change ---

	/**
	 * The saga's third part: visit has made the step's change, so the roster change is settled the way
	 * the decision said, and everybody is told. Runs once per step, in the transaction that marks it done
	 * ({@link VisitChangeSaga}); a cover carrying on ({@link LeaveCoverService}) is settled here too.
	 *
	 * @return the roster change the step settled
	 */
	Long settle(VisitChangeStep made) {
		return switch (made.request().kind()) {
			case REASSIGN -> replaced(made);
			case MARK_UNCOVERED -> leftUncovered(made);
			case CALL_OFF -> skipped(made);
			case MOVE -> moved(made);
		};
	}

	/** The visit went to another caregiver: everybody is told; a default plan is audited. */
	private Long replaced(VisitChangeStep made) {
		LocalDateTime now = now();
		VisitChangeStep.Decision decision = made.decision();
		Long caregiverId = made.request().toCaregiverId();
		RosterChange change = made.rosterChangeId() == null
				// A cover carrying on: its change is recorded once the visit is theirs
				? RosterChange.offered(made.absenceId(), made.visit(), decision.runId(), caregiverId, now, now)
				: lock(made.rosterChangeId());
		search.select(made.request().rosteringCandidateId());
		RosterChange settled = changes.save(change.replacedBy(caregiverId, decision.runId(), decision.decidedBy(),
				decision.decidedByUserId(), decision.note(), now));
		String forFamily = decision.decidedBy() == RosterChange.DecidedBy.FAMILY ? null : decision.note();
		alert.settled(settled, notice(settled, decision.caregiverName(), caregiverId, null, forFamily));
		if (decision.decidedBy() == RosterChange.DecidedBy.DEFAULT_PLAN) {
			audit.defaultPlanApplied(settled, "%s; caregiver %d assigned to visit %d"
					.formatted(decision.note(), caregiverId, made.visit().visitId()));
		}
		return settled.id();
	}

	/** The visit is an exception with nobody on it: an incident gives it a responder, and the family hears of it. */
	private Long leftUncovered(VisitChangeStep made) {
		VacatedSlot slot = made.visit();
		VisitChangeStep.Decision decision = made.decision();
		Long incidentId = incidents.raiseForUnfilledAbsence(slot.elderId(), slot.visitId(),
				"%s visit at %s: the caregiver is absent and nobody is free to replace them".formatted(
						slot.serviceType() == null ? "A" : slot.serviceType(), slot.start().format(WHEN))).id();
		RosterChange change = made.rosterChangeId() == null
				? RosterChange.uncovered(made.absenceId(), slot, decision.runId(), incidentId, now())
				: lock(made.rosterChangeId()).leftUncovered(incidentId, decision.runId(), decision.note(), now());
		RosterChange saved = changes.save(change);
		alert.coordinating(saved, notice(saved, null, null, null, null));
		return saved.id();
	}

	/** The visit is called off: the family's own cancellation. */
	private Long skipped(VisitChangeStep made) {
		VisitChangeStep.Decision decision = made.decision();
		RosterChange settled = changes.save(lock(made.rosterChangeId()).skipped(decision.decidedByUserId(),
				decision.note(), now()));
		alert.settled(settled, notice(settled, null, null, null, null));
		return settled.id();
	}

	/**
	 * The visit has moved: the search is filed under the visit that replaced it, everybody is told, and
	 * the family is offered the choice for the new time.
	 */
	private Long moved(VisitChangeStep made) {
		LocalDateTime now = now();
		VisitChangeStep.Decision decision = made.decision();
		VisitChangeStep.Request request = made.request();
		Long newVisitId = made.resultVisitId();
		candidates.moveToVisit(decision.runId(), made.visit().visitId(), newVisitId);
		RosterChange settled = changes.save(lock(made.rosterChangeId()).rescheduled(newVisitId,
				request.toCaregiverId(), decision.runId(), decision.decidedByUserId(), decision.note(), now));
		alert.settled(settled, notice(settled, decision.caregiverName(), request.toCaregiverId(), request.newStart(),
				null));
		offerTheNewTime(settled, newVisitId, decision.runId(), request.toCaregiverId(), decision.caregiverName(), now);
		return settled.id();
	}

	// ------------------------------------------------------------------ helpers ---

	/**
	 * Visits the absence vacates that have no change yet: still with the absent caregiver, not started, still
	 * ahead, and not held by a change to them that visit has not made yet.
	 */
	private List<VacatedSlot> unhandled(AbsenceReport absence, Map<Long, RosterChange> byVisit, Set<Long> held,
			LocalDateTime now) {
		Set<Long> handled = new HashSet<>(byVisit.keySet());
		handled.addAll(held);
		return Vacancies.notYetRerostered(visits, absence, handled, now).stream()
				.map(visit -> toSlot(visit, absence.caregiverId()))
				.toList();
	}

	/** Visits an earlier run left uncovered that are still ahead, still uncovered, and not being changed. */
	private List<VacatedSlot> uncoveredToRetry(AbsenceReport absence, Collection<RosterChange> existing,
			Set<Long> held, LocalDateTime now) {
		return existing.stream()
				.filter(RosterChange::isUncovered)
				.filter(change -> !held.contains(change.visitId()))
				.map(change -> visits.find(change.visitId()))
				.flatMap(Optional::stream)
				.filter(visit -> "EXCEPTION".equals(visit.status()) && visit.caregiverId() == null)
				.filter(visit -> visit.start().isAfter(now))
				.map(visit -> toSlot(visit, absence.caregiverId()))
				.toList();
	}

	/** The change's visit, which must still be waiting to happen. */
	private VacatedSlot openSlot(RosterChange change) {
		VisitReassignment.VisitSlot visit = visits.find(change.visitId())
				.filter(v -> "SCHEDULED".equals(v.status()))
				.orElseThrow(() -> new BusinessRuleViolation("VISIT_NOT_OPEN",
						"The visit has been started or called off since the change was offered"));
		return toSlot(visit, change.originalCaregiverId());
	}

	/**
	 * The saga's semantic lock: nothing is decided for a visit while a change to it is still being made,
	 * or has been given up and waits for a manager.
	 */
	private void requireNoChangeUnderWay(RosterChange change) {
		List<VisitChangeStep> held = saga.holding(change.absenceId()).stream()
				.filter(step -> step.visit().visitId().equals(change.visitId()))
				.toList();
		if (!held.isEmpty()) {
			throw changesUnderWay(held);
		}
	}

	private static BusinessRuleViolation changesUnderWay(List<VisitChangeStep> held) {
		long abandoned = held.stream().filter(VisitChangeStep::isAbandoned).count();
		if (abandoned > 0) {
			return new BusinessRuleViolation("CHANGE_IN_PROGRESS",
					"%d change%s to visits could not reach the visit service; a manager's re-roster sends %s again"
							.formatted(abandoned, abandoned == 1 ? "" : "s", abandoned == 1 ? "it" : "them"));
		}
		return new BusinessRuleViolation("CHANGE_IN_PROGRESS", "%d change%s to visits %s still being made"
				.formatted(held.size(), held.size() == 1 ? "" : "s", held.size() == 1 ? "is" : "are"));
	}

	private RosterChange lock(Long changeId) {
		return changes.lock(changeId).orElseThrow(() -> new ResourceNotFound("RosterChange", changeId));
	}

	private RosterChange current(Long changeId) {
		return changes.findById(changeId).orElseThrow(() -> new ResourceNotFound("RosterChange", changeId));
	}

	private Map<Long, RosterChange> changesByVisit(Long absenceId) {
		return changes.findByAbsenceId(absenceId).stream()
				.collect(Collectors.toMap(RosterChange::visitId, Function.identity(), (a, b) -> a));
	}

	private RosteringRun.Objective objectiveOf(RosterChange change) {
		return change.rosteringRunId() == null
				? RosteringRun.Objective.CONTINUITY
				: runs.findById(change.rosteringRunId()).map(RosteringRun::objective)
						.filter(objective -> objective != RosteringRun.Objective.COST)
						.orElse(RosteringRun.Objective.CONTINUITY);
	}

	private AbsenceReport requireApproved(Long absenceId) {
		AbsenceReport absence = absences.findById(absenceId)
				.orElseThrow(() -> new ResourceNotFound("Absence", absenceId));
		if (!absence.isApproved()) {
			throw new BusinessRuleViolation("ABSENCE_NOT_APPROVED",
					"Only an approved absence vacates visits; this one is " + absence.status());
		}
		return absence;
	}

	private RosterChangeAlert.Notice notice(RosterChange change, String caregiverName, Long caregiverId,
			LocalDateTime newStart, String why) {
		String elderName = profiles.elder(change.elderId()).map(RosteringProfiles.ElderFacts::fullName).orElse(null);
		return new RosterChangeAlert.Notice(elderName, caregiverName, caregiverId, newStart, why);
	}

	private String nameOf(Long caregiverId) {
		return profiles.candidates().stream()
				.filter(c -> Objects.equals(c.caregiverId(), caregiverId))
				.map(RosteringProfiles.Candidate::fullName)
				.findFirst()
				.orElse("caregiver " + caregiverId);
	}

	private static String describe(RosterChange change) {
		return change.status() == RosterChange.Status.UNCOVERED
				? "nobody could be found, and a manager is handling it"
				: "%s by %s".formatted(change.outcome(), change.decidedBy());
	}

	private static String byWhom(RosterChange.DecidedBy by) {
		return switch (by) {
			case FAMILY -> "Chosen by the family";
			case DEFAULT_PLAN -> "Default plan";
			case MANAGER -> "Re-rostered by a manager";
		};
	}

	private static VacatedSlot toSlot(VisitReassignment.VisitSlot visit, Long vacatedBy) {
		return new VacatedSlot(visit.visitId(), visit.elderId(), visit.carePlanId(), visit.serviceType(), visit.start(),
				visit.end(), vacatedBy);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	/**
	 * What one re-roster did.
	 *
	 * @param runId null when there was nothing left to re-roster
	 * @param offered visits now waiting for the family
	 * @param settled visits settled at once: too close to ask, or uncovered ones a manager took up
	 * @param uncovered visits nobody could take
	 */
	public record ReRosterOutcome(Long absenceId, Long runId, int searched, int offered, int settled, int uncovered) {
	}
}
