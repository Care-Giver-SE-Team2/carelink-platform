package sg.nus.carelink.visit.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Incidents a caregiver reports from a visit, which core's incident module keeps and follows up. */
public interface CaregiverIncidentGateway {

	/**
	 * Reports the incident in core, once per {@code clientRequestId}: a retry of the same report, after visit's
	 * own transaction failed, gets the incident the first try raised.
	 */
	Report report(Long elderId, Long visitId, Long actor, String category, String severity, String description,
			UUID clientRequestId);

	/** One of the caregiver's own reports; another's is not found. */
	Report own(Long actor, Long incidentId);

	Reports list(Long actor, Long visitId, int page, int size);

	record Report(Long id, Long visitId, String category, String severity, String description, String status,
			LocalDateTime reportedAt, LocalDateTime respondBy, LocalDateTime resolvedAt) {
	}

	record Reports(List<Report> items, int page, int size, long totalElements) {
	}

}
