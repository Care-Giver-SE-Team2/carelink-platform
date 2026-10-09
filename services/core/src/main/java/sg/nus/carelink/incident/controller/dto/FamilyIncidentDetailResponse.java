package sg.nus.carelink.incident.controller.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import sg.nus.carelink.incident.application.FamilyIncidentQueryService;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.IncidentAcknowledgement;

/**
 * Explicit family projection, excluding internal handling and other recipients' data.
 *
 * @author Wang Zhili
 */
public record FamilyIncidentDetailResponse(
		Long id, Long elderId, Long visitId, Incident.Source source, Incident.Category category,
		Incident.Severity severity, Incident.Status status, String description,
		OffsetDateTime reportedAt, OffsetDateTime resolvedAt, OffsetDateTime acknowledgeBy,
		Acknowledgement acknowledgement) {

	public static FamilyIncidentDetailResponse of(FamilyIncidentQueryService.Detail detail) {
		var incident = detail.incident();
		var receipt = detail.acknowledgement();
		return new FamilyIncidentDetailResponse(incident.id(), incident.elderId(), incident.visitId(),
				incident.source(), incident.category(), incident.severity(), incident.status(), incident.description(),
				time(incident.reportedAt()), time(incident.resolvedAt()), time(detail.acknowledgeBy()),
				Acknowledgement.of(receipt));
	}

	private static OffsetDateTime time(LocalDateTime value) {
		return value == null ? null : value.atZone(Incident.CARELINK_ZONE).toOffsetDateTime();
	}

	public record Acknowledgement(Long id, Long incidentId, Long familyMemberId,
			OffsetDateTime viewedAt, OffsetDateTime acknowledgedAt, String responseNote) {

		public static Acknowledgement of(IncidentAcknowledgement receipt) {
			return new Acknowledgement(receipt.id(), receipt.incidentId(), receipt.familyMemberId(),
					time(receipt.viewedAt()), time(receipt.acknowledgedAt()), receipt.responseNote());
		}
	}
}
