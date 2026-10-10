package sg.nus.carelink.visit.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The reads of the visit table that other modules make today with statements of their own: an
 * elder's coming visits, for planning a spot check, and the caregiver on the elder's most recent
 * visit, for the alert that goes to them (incident); whether a caregiver is busy at a time, before
 * an extra service is handed to them (report). visit answers them on its internal API, so those
 * modules can ask visit instead of reading its table once visit is a service of its own.
 */
public interface VisitLookups {

	/** The elder's scheduled visits with a caregiver that begin in [from, until), earliest first. */
	List<ElderVisit> upcomingVisits(Long elderId, LocalDateTime from, LocalDateTime until);

	/** The caregiver on the elder's most recent visit that had one. */
	Optional<Long> latestCaregiverId(Long elderId);

	/**
	 * Whether the caregiver has a visit booked or under way that overlaps [from, until); a visit
	 * with no end counts as an hour long. Value-added dispatch asks it before it hands the
	 * caregiver an extra service.
	 */
	boolean caregiverBusy(Long caregiverId, LocalDateTime from, LocalDateTime until);

	record ElderVisit(Long visitId, Long elderId, Long caregiverId, LocalDateTime start, String status,
			String serviceType) {
	}

}
