package sg.nus.carelink.incident.application;

import java.time.LocalDateTime;
import java.util.List;

/** Cross-module contract: caregiver responses never expose manager internal records. */
public interface CaregiverIncidentGateway {
    Report report(Long elderId, Long visitId, Long actor, String category, String severity, String description);
    Report own(Long actor, Long incidentId);
    Reports list(Long actor, Long visitId, int page, int size);
    record Report(Long id, Long visitId, String category, String severity, String description, String status,
                  LocalDateTime reportedAt, LocalDateTime respondBy, LocalDateTime resolvedAt) {}
    record Reports(List<Report> items, int page, int size, long totalElements) {}
}
