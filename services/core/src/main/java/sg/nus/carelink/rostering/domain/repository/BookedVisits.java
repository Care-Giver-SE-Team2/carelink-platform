package sg.nus.carelink.rostering.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import sg.nus.carelink.rostering.domain.model.VisitsAtRisk.Booking;

/**
 * The visits caregivers are booked on and have not started, which UC-MG06 counts against each
 * lapsing certificate. visit owns them. Rostering asks this port, which it owns, and never
 * visit's classes: while visit runs inside core, an adapter asks visit's service; once visit runs
 * on its own, an adapter asks visit's internal API.
 */
public interface BookedVisits {

	/** Assigned visits nobody has started that begin in [from, until), earliest first. */
	List<Booking> unstartedBetween(LocalDateTime from, LocalDateTime until);

}
