package sg.nus.carelink.visit.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cross-module contract for UC-MG03: rostering decides which visits a care plan needs and who
 * takes them; this module owns how a visit comes into being and how it is called off.
 * Rostering imports this interface only, never visit's domain or infrastructure.
 *
 * <p>Both operations are safe to repeat: a visit is identified by its plan task and start
 * time, so running the same request twice changes nothing the second time.
 */
public interface VisitScheduling {

	/**
	 * Creates each requested visit that does not exist yet, and gives an existing visit that
	 * is still unassigned and untouched to the requested caregiver. An existing visit that is
	 * assigned, started, finished or cancelled is left as it is.
	 */
	Outcome schedule(List<PlannedVisit> visits);

	/**
	 * Cancels one plan version's visits that start at or after {@code from} and that nobody
	 * has started. Visits already under way or finished keep pointing at the version they
	 * were made from.
	 *
	 * @return how many visits were cancelled
	 */
	int cancelUntouchedFrom(Long carePlanId, LocalDateTime from);

	/**
	 * Visits nobody is assigned to and nobody has started that began at or after
	 * {@code since} and before {@code now}, earliest first.
	 */
	List<UncoveredVisit> findUncoveredStarted(LocalDateTime since, LocalDateTime now);

	/**
	 * Marks one uncovered visit as an exception. False, and nothing changes, when it was
	 * covered or started in the meantime.
	 */
	boolean markUncoveredAsException(Long visitId);

	/** A visit that reached its start time with nobody assigned. */
	record UncoveredVisit(Long visitId, Long elderId, LocalDateTime start, String serviceType) {
	}

	/** One visit a care plan task calls for; {@code caregiverId} null leaves it to be covered. */
	record PlannedVisit(Long elderId, Long caregiverId, Long carePlanId, Long carePlanNodeId,
			String serviceType, LocalDateTime start, LocalDateTime end) {
	}

	record Outcome(int created, int covered) {
	}
}
