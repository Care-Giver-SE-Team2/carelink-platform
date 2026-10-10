package sg.nus.carelink.visit.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cross-module contract for UC-MG06: the visits caregivers are booked on and have not yet
 * started, so rostering can count the ones a lapsing certificate puts at risk. Rostering asks
 * its own port, {@code BookedVisits}; only its in-process adapter imports this interface, never
 * visit's domain or infrastructure. Outside core, {@code VisitApi.unstartedBetween} serves it.
 */
public interface UpcomingAssignments {

	/** Assigned visits nobody has started that begin in [from, until), earliest first. */
	List<Assignment> unstartedBetween(LocalDateTime from, LocalDateTime until);

	/** One booked visit: who takes it, which plan version it came from, and when it starts. */
	record Assignment(Long visitId, Long caregiverId, Long carePlanId, LocalDateTime start) {
	}
}
