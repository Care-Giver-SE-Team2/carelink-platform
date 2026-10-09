package sg.nus.carelink.rostering.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One run of the replacement search: which objective ranked the candidates, what triggered it,
 * and how much of what it was asked to cover it could (DECISION 17 in the schema: a run is
 * recorded, not only its outcome).
 *
 * <p>UC-MG04 records a run each time the search runs - when the manager re-rosters an absence,
 * when a choice is checked again just before it is applied, and when a family asks for another
 * time - so every suggestion anybody was shown can be traced to the run that made it.
 */
public record RosteringRun(
		Long id,
		RosteringRun.TriggerType triggerType,
		Long absenceId,
		RosteringRun.Objective objective,
		Long requestedByUserId,
		RosteringRun.Status status,
		Integer visitsTotal,
		Integer visitsCovered,
		Integer continuityKept,
		BigDecimal addedTravelKm,
		LocalDateTime ranAt,
		LocalDateTime committedAt) {

	/** A search for the visits an absence vacated, before anything is put into effect. */
	public static RosteringRun forAbsence(Long absenceId, Objective objective, Long requestedByUserId,
			LocalDateTime now) {
		return new RosteringRun(null, TriggerType.ABSENCE, Objects.requireNonNull(absenceId, "absenceId"),
				objective == null ? Objective.CONTINUITY : objective, requestedByUserId, Status.PROPOSED, 0, 0, 0,
				null, Objects.requireNonNull(now, "now"), null);
	}

	/**
	 * The run's proposals have been put into effect - offered to families or applied.
	 *
	 * @param visitsCovered visits somebody could take
	 * @param continuityKept of those, visits whose best replacement had been to the elder before
	 */
	public RosteringRun committed(int visitsTotal, int visitsCovered, int continuityKept, LocalDateTime now) {
		return new RosteringRun(id, triggerType, absenceId, objective, requestedByUserId, Status.COMMITTED,
				visitsTotal, visitsCovered, continuityKept, addedTravelKm, ranAt, now);
	}

	public enum TriggerType {
		NEW_VISIT, ABSENCE, MANUAL
	}

	public enum Objective {
		CONTINUITY, TRAVEL_TIME, EVEN_WORKLOAD, COST
	}

	public enum Status {
		PROPOSED, COMMITTED, DISCARDED
	}
}
