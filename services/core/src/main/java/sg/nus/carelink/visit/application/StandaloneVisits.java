package sg.nus.carelink.visit.application;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Cross-module contract for visits no care plan produces - today the work order of an approved
 * extra service (UC-FM08). The report module creates them and follows them through this
 * interface only, never visit's domain or infrastructure.
 */
public interface StandaloneVisits {

	/**
	 * Books the visit, SCHEDULED, with its one task to be the service itself. {@code caregiverId}
	 * null leaves it for a manager to staff.
	 *
	 * @return the new visit's id
	 */
	Long schedule(NewVisit visit);

	/** Where a visit stands, as a request that dispatched it needs to know. */
	Optional<State> find(Long visitId);

	/**
	 * @param end null when the length is not known
	 * @param instructions shown to the caregiver with the visit; null for none
	 */
	record NewVisit(Long elderId, Long caregiverId, String serviceType, LocalDateTime start, LocalDateTime end,
			String instructions) {
	}

	/**
	 * @param status the visit's state, e.g. SCHEDULED or EXCEPTION
	 * @param checkedIn whether a caregiver ever checked in, i.e. whether any of the service was given
	 */
	record State(Long visitId, Long caregiverId, String status, boolean checkedIn) {
	}
}
