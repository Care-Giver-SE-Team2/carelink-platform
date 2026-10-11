package sg.nus.carelink.rostering.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * One change UC-MG04 asks visit to make to a visit - give it to another caregiver, leave it uncovered,
 * call it off or move it - as a step of the re-rostering saga. Re-rostering decides in core's database and
 * visit changes the visit in its own, so the two cannot share a transaction. Instead the decision writes
 * the step down with a request key, visit is asked after that commits, and core settles the roster change
 * and tells people only once visit has made the change (DONE). A refusal (REJECTED) changes nothing in
 * core and frees the visit for the next decision; a visit that cannot be reached is asked again with the
 * same key, so visit makes the change once however often it is asked, until the retries run out
 * (ABANDONED) and a manager takes over.
 *
 * <pre>
 *   PENDING   --visit made the change--------------> DONE
 *   PENDING   --visit refused it (409, 404)---------> REJECTED
 *   PENDING   --no answer, a retry left-------------> PENDING (one more failed try, the next one later)
 *   PENDING   --no answer, no retry left------------> ABANDONED
 *   ABANDONED --a manager re-rosters the absence----> PENDING (the same key, the retries afresh)
 * </pre>
 *
 * <p>While PENDING or ABANDONED a step holds its visit: nothing else is decided for it (the saga's
 * semantic lock). A record: every transition returns a new instance, and an illegal one throws
 * {@link BusinessRuleViolation}.
 *
 * @param rosterChangeId the change the step settles; null while the change is only to be recorded once the
 *     step is done (a visit left uncovered by a re-roster, a cover carrying on)
 * @param visit the visit as the decision saw it
 * @param attempts tries that did not get through
 * @param nextTryAt when the sweep may try a PENDING step again
 * @param resultVisitId the visit a move created
 * @param incidentId the incident a manager got when the step was abandoned
 */
public record VisitChangeStep(
		Long id,
		Long absenceId,
		Long rosterChangeId,
		VacatedSlot visit,
		Request request,
		Decision decision,
		String requestKey,
		Status status,
		int attempts,
		LocalDateTime nextTryAt,
		String lastError,
		Long resultVisitId,
		Long incidentId,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	/** The longest error kept, as the column holds it. */
	private static final int ERROR_LENGTH = 500;

	public VisitChangeStep {
		Objects.requireNonNull(absenceId, "absenceId");
		Objects.requireNonNull(visit, "visit");
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(decision, "decision");
		Objects.requireNonNull(requestKey, "requestKey");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(nextTryAt, "nextTryAt");
	}

	/**
	 * A change just decided, with a request key of its own. The request that decided it makes it at once;
	 * the sweep takes it over from {@code nextTryAt} if the request did not get to it.
	 */
	public static VisitChangeStep decided(Long absenceId, Long rosterChangeId, VacatedSlot visit, Request request,
			Decision decision, LocalDateTime nextTryAt, LocalDateTime now) {
		return new VisitChangeStep(null, absenceId, rosterChangeId, visit, request, decision,
				"visit-change-" + UUID.randomUUID(), Status.PENDING, 0, nextTryAt, null, null, null, now, now);
	}

	/** visit made the change; {@code newVisitId} is the visit a move created. */
	public VisitChangeStep made(Long newVisitId, LocalDateTime now) {
		requirePending("made");
		if (request.kind() == Kind.MOVE) {
			Objects.requireNonNull(newVisitId, "newVisitId");
		}
		return with(Status.DONE, attempts, nextTryAt, lastError, newVisitId, now);
	}

	/** visit refused the change: nothing was changed, and the step lets go of its visit. */
	public VisitChangeStep refused(String why, LocalDateTime now) {
		requirePending("refused");
		return with(Status.REJECTED, attempts, nextTryAt, cut(why), resultVisitId, now);
	}

	/**
	 * The try did not get through - no connection, no answer in time, a server error. visit may or may
	 * not have made the change, so the step is tried again with the same key while {@code retries} allow,
	 * and abandoned when they do not.
	 */
	public VisitChangeStep notThrough(String error, LocalDateTime now, VisitChangeRetries retries) {
		requirePending("tried again");
		int failures = attempts + 1;
		return retries.nextTry(failures, now, visit.start())
				.map(next -> with(Status.PENDING, failures, next, cut(error), resultVisitId, now))
				.orElseGet(() -> with(Status.ABANDONED, failures, nextTryAt, cut(error), resultVisitId, now));
	}

	/** The incident a manager got for an abandoned step. */
	public VisitChangeStep handedToManager(Long incident) {
		Objects.requireNonNull(incident, "incident");
		if (status != Status.ABANDONED) {
			throw notAllowed("handed to a manager");
		}
		return new VisitChangeStep(id, absenceId, rosterChangeId, visit, request, decision, requestKey, status,
				attempts, nextTryAt, lastError, resultVisitId, incident, createdAt, updatedAt);
	}

	/** A manager re-rosters the absence: the abandoned step is sent again, with its key and fresh retries. */
	public VisitChangeStep sentAgain(LocalDateTime nextTry, LocalDateTime now) {
		if (status != Status.ABANDONED) {
			throw notAllowed("sent again");
		}
		return with(Status.PENDING, 0, nextTry, lastError, resultVisitId, now);
	}

	/** The roster change the finished step settled, once it is recorded. */
	public VisitChangeStep settling(Long changeId) {
		Objects.requireNonNull(changeId, "changeId");
		return new VisitChangeStep(id, absenceId, changeId, visit, request, decision, requestKey, status, attempts,
				nextTryAt, lastError, resultVisitId, incidentId, createdAt, updatedAt);
	}

	/** PENDING or ABANDONED: the change is decided but not known to be made, so nothing else may touch the visit. */
	public boolean holdsVisit() {
		return status == Status.PENDING || status == Status.ABANDONED;
	}

	public boolean isPending() {
		return status == Status.PENDING;
	}

	public boolean isAbandoned() {
		return status == Status.ABANDONED;
	}

	private VisitChangeStep with(Status newStatus, int newAttempts, LocalDateTime newNextTryAt, String error,
			Long newVisitId, LocalDateTime now) {
		Objects.requireNonNull(now, "now");
		return new VisitChangeStep(id, absenceId, rosterChangeId, visit, request, decision, requestKey, newStatus,
				newAttempts, newNextTryAt, error, newVisitId, incidentId, createdAt, now);
	}

	private void requirePending(String verb) {
		if (status != Status.PENDING) {
			throw notAllowed(verb);
		}
	}

	private BusinessRuleViolation notAllowed(String verb) {
		return new BusinessRuleViolation("VISIT_CHANGE_NOT_OPEN",
				"The change to visit %d cannot be %s: it is %s".formatted(visit.visitId(), verb, status));
	}

	private static String cut(String text) {
		return text == null || text.length() <= ERROR_LENGTH ? text : text.substring(0, ERROR_LENGTH - 3) + "...";
	}

	/**
	 * What visit is asked to do, with what its assignment history records about why.
	 *
	 * @param toCaregiverId who takes the visit; null when nobody does
	 * @param newStart a move's new time
	 * @param byUserId who visit's history names; null when nobody acted
	 * @param rosteringCandidateId the suggestion the new caregiver came from, if any
	 */
	public record Request(Kind kind, Long toCaregiverId, LocalDateTime newStart, Long byUserId,
			Long rosteringCandidateId, String reason) {

		public Request {
			Objects.requireNonNull(kind, "kind");
		}

		public static Request reassign(Long toCaregiverId, Long byUserId, Long rosteringCandidateId, String reason) {
			return new Request(Kind.REASSIGN, Objects.requireNonNull(toCaregiverId, "toCaregiverId"), null, byUserId,
					rosteringCandidateId, reason);
		}

		public static Request markUncovered(String reason) {
			return new Request(Kind.MARK_UNCOVERED, null, null, null, null, reason);
		}

		public static Request callOff(Long byUserId, String reason) {
			return new Request(Kind.CALL_OFF, null, null, byUserId, null, reason);
		}

		public static Request move(LocalDateTime newStart, Long toCaregiverId, Long byUserId, String reason) {
			return new Request(Kind.MOVE, Objects.requireNonNull(toCaregiverId, "toCaregiverId"),
					Objects.requireNonNull(newStart, "newStart"), byUserId, null, reason);
		}
	}

	/**
	 * What core records once visit has made the change: the roster change's run, who decided, and the
	 * words people are told.
	 *
	 * @param decidedByUserId the person who decided - a manager or a family member - told if visit refuses
	 *     the change after they have had their answer; null when the system decided
	 * @param caregiverName who is coming, as the family is told
	 * @param note the roster change's note once settled
	 */
	public record Decision(Long runId, RosterChange.DecidedBy decidedBy, Long decidedByUserId, String caregiverName,
			String note) {
	}

	/** The four changes UC-MG04 makes to a visit. */
	public enum Kind {
		REASSIGN, MARK_UNCOVERED, CALL_OFF, MOVE
	}

	public enum Status {
		PENDING, DONE, REJECTED, ABANDONED
	}
}
