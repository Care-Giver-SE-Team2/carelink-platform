package sg.nus.carelink.rostering.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * What became of one visit an absence vacated (UC-MG04): the aggregate the family answers and
 * the default plan settles.
 *
 * <p>It starts in one of two states. With somebody free, it is {@code AWAITING_FAMILY}: the
 * best replacement is proposed and the family has until {@code respondBy} to keep them, pick
 * another suggestion, move the visit or skip it. With nobody free, it is {@code UNCOVERED}
 * and an incident has been raised, so the gap has a responder instead of sitting in a list
 * (exception 3a). Either way it ends {@code RESOLVED}, with an {@code outcome} saying what
 * happened and {@code decidedBy} saying who decided - the use case asks for a default plan to
 * be told apart from a family's choice.
 *
 * <pre>
 *   AWAITING_FAMILY --family or default plan--> RESOLVED (REPLACED | RESCHEDULED | SKIPPED)
 *   AWAITING_FAMILY --nobody free any more----> UNCOVERED
 *   UNCOVERED       --manager re-rosters------> RESOLVED (REPLACED)
 *   either open state --visit called off elsewhere--> RESOLVED (WITHDRAWN)
 * </pre>
 *
 * <p>A record: every transition returns a new instance, and an illegal one throws
 * {@link BusinessRuleViolation}, which reaches the client as HTTP 409.
 */
public record RosterChange(
		Long id,
		Long absenceId,
		Long visitId,
		Long elderId,
		Long originalCaregiverId,
		LocalDateTime visitStart,
		LocalDateTime visitEnd,
		Long rosteringRunId,
		Long proposedCaregiverId,
		RosterChange.Status status,
		RosterChange.Outcome outcome,
		RosterChange.DecidedBy decidedBy,
		Long decidedByUserId,
		Long assignedCaregiverId,
		Long rescheduledVisitId,
		Long incidentId,
		LocalDateTime respondBy,
		LocalDateTime decidedAt,
		String note,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public RosterChange {
		Objects.requireNonNull(absenceId, "absenceId");
		Objects.requireNonNull(visitId, "visitId");
		Objects.requireNonNull(status, "status");
	}

	/** Step 4: somebody is free, and the family is asked before anything changes. */
	public static RosterChange offered(Long absenceId, VacatedSlot slot, Long runId, Long proposedCaregiverId,
			LocalDateTime respondBy, LocalDateTime now) {
		Objects.requireNonNull(proposedCaregiverId, "proposedCaregiverId");
		Objects.requireNonNull(respondBy, "respondBy");
		return new RosterChange(null, absenceId, slot.visitId(), slot.elderId(), slot.vacatedByCaregiverId(),
				slot.start(), slot.end(), runId, proposedCaregiverId, Status.AWAITING_FAMILY, null, null, null,
				null, null, null, respondBy, null, null, now, now);
	}

	/** Exception 3a: nobody is free; the visit is an exception with an incident behind it. */
	public static RosterChange uncovered(Long absenceId, VacatedSlot slot, Long runId, Long incidentId,
			LocalDateTime now) {
		return new RosterChange(null, absenceId, slot.visitId(), slot.elderId(), slot.vacatedByCaregiverId(),
				slot.start(), slot.end(), runId, null, Status.UNCOVERED, null, null, null, null, null, incidentId,
				null, null, "Nobody was free to cover this visit", now, now);
	}

	/**
	 * The visit goes to another caregiver: the family's pick, the default plan when they did
	 * not answer, or a manager taking up an uncovered visit once somebody is free.
	 */
	public RosterChange replacedBy(Long caregiverId, Long runId, DecidedBy by, Long userId, String why,
			LocalDateTime now) {
		Objects.requireNonNull(caregiverId, "caregiverId");
		Objects.requireNonNull(by, "by");
		if (by == DecidedBy.MANAGER ? status != Status.UNCOVERED : status != Status.AWAITING_FAMILY) {
			throw notSettleable("given to another caregiver by " + by);
		}
		return settled(Outcome.REPLACED, by, userId, caregiverId, null, runId, why, now);
	}

	/** Alternative 4b: the family moves the visit; whoever is free then takes it. */
	public RosterChange rescheduled(Long newVisitId, Long caregiverId, Long runId, Long familyUserId, String why,
			LocalDateTime now) {
		Objects.requireNonNull(newVisitId, "newVisitId");
		Objects.requireNonNull(caregiverId, "caregiverId");
		requireAwaitingFamily("moved");
		return settled(Outcome.RESCHEDULED, DecidedBy.FAMILY, familyUserId, caregiverId, newVisitId, runId, why, now);
	}

	/**
	 * Alternative 4c: the family skips this visit. Not a missed visit, because nobody failed
	 * to turn up; it still counts against the period's fulfilment.
	 */
	public RosterChange skipped(Long familyUserId, String why, LocalDateTime now) {
		requireAwaitingFamily("skipped");
		return settled(Outcome.SKIPPED, DecidedBy.FAMILY, familyUserId, null, null, rosteringRunId, why, now);
	}

	/**
	 * Whoever was proposed is no longer free and nobody else is either (exception 5a run out of
	 * options): the visit becomes uncovered and an incident is raised for it.
	 */
	public RosterChange leftUncovered(Long incidentId, Long runId, String why, LocalDateTime now) {
		Objects.requireNonNull(incidentId, "incidentId");
		requireAwaitingFamily("left uncovered");
		return new RosterChange(id, absenceId, visitId, elderId, originalCaregiverId, visitStart, visitEnd,
				runId == null ? rosteringRunId : runId, proposedCaregiverId, Status.UNCOVERED, null, null, null,
				null, null, incidentId, respondBy, null, why, createdAt, now);
	}

	/**
	 * The visit stopped being this change's business before it was settled: called off because
	 * its care plan ended, say, or started by somebody. Nobody decided, so nobody is named.
	 */
	public RosterChange withdrawn(String why, LocalDateTime now) {
		if (status == Status.RESOLVED) {
			throw notSettleable("withdrawn");
		}
		return settled(Outcome.WITHDRAWN, null, null, null, null, rosteringRunId, why, now);
	}

	/** A manager re-ran the search for an uncovered visit and still nobody is free. */
	public RosterChange searchedAgain(Long runId, LocalDateTime now) {
		if (status != Status.UNCOVERED) {
			throw notSettleable("searched again");
		}
		return new RosterChange(id, absenceId, visitId, elderId, originalCaregiverId, visitStart, visitEnd, runId,
				proposedCaregiverId, status, outcome, decidedBy, decidedByUserId, assignedCaregiverId,
				rescheduledVisitId, incidentId, respondBy, decidedAt, note, createdAt, now);
	}

	public boolean awaitingFamily() {
		return status == Status.AWAITING_FAMILY;
	}

	public boolean isUncovered() {
		return status == Status.UNCOVERED;
	}

	/** Still waiting for the family, and their time is up: the default plan is due. */
	public boolean defaultPlanDue(LocalDateTime now) {
		return awaitingFamily() && !now.isBefore(respondBy);
	}

	/** Whether the family may still answer; a late answer would race the default plan. */
	public boolean familyMayAnswer(LocalDateTime now) {
		return awaitingFamily() && now.isBefore(respondBy);
	}

	private RosterChange settled(Outcome how, DecidedBy by, Long userId, Long caregiverId, Long newVisitId,
			Long runId, String why, LocalDateTime now) {
		Objects.requireNonNull(now, "now");
		return new RosterChange(id, absenceId, visitId, elderId, originalCaregiverId, visitStart, visitEnd,
				runId == null ? rosteringRunId : runId, proposedCaregiverId, Status.RESOLVED, how, by, userId,
				caregiverId, newVisitId, incidentId, respondBy, now, why, createdAt, now);
	}

	private void requireAwaitingFamily(String verb) {
		if (status != Status.AWAITING_FAMILY) {
			throw notSettleable(verb);
		}
	}

	private BusinessRuleViolation notSettleable(String verb) {
		String state = status == Status.RESOLVED
				? "already settled (" + outcome + " by " + decidedBy + ")"
				: status.name();
		return new BusinessRuleViolation("ROSTER_CHANGE_NOT_OPEN",
				"The change to visit %d cannot be %s: it is %s".formatted(visitId, verb, state));
	}

	public enum Status {
		AWAITING_FAMILY, UNCOVERED, RESOLVED
	}

	public enum Outcome {
		REPLACED, RESCHEDULED, SKIPPED, WITHDRAWN
	}

	public enum DecidedBy {
		FAMILY, DEFAULT_PLAN, MANAGER
	}
}
