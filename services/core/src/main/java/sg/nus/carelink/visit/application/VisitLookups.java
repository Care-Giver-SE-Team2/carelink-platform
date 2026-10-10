package sg.nus.carelink.visit.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The reads of the visit table that incident makes today with statements of its own: an elder's
 * coming visits, for planning a spot check, and the caregiver on the elder's most recent visit, for
 * the alert that goes to them. visit answers them on its internal API, so incident can ask visit
 * instead of reading its table once visit is a service of its own.
 */
public interface VisitLookups {

	/** The elder's scheduled visits with a caregiver that begin in [from, until), earliest first. */
	List<ElderVisit> upcomingVisits(Long elderId, LocalDateTime from, LocalDateTime until);

	/** The caregiver on the elder's most recent visit that had one. */
	Optional<Long> latestCaregiverId(Long elderId);

	record ElderVisit(Long visitId, Long elderId, Long caregiverId, LocalDateTime start, String status,
			String serviceType) {
	}

}
