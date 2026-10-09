package sg.nus.carelink.incident.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Independent family display and acknowledgement receipts with immutable first values.
 *
 * @author Wang Zhili
 */
public record IncidentAcknowledgement(
		Long id,
		Long incidentId,
		Long familyMemberId,
		LocalDateTime viewedAt,
		LocalDateTime acknowledgedAt,
		String responseNote,
		LocalDateTime createdAt) {

	public IncidentAcknowledgement viewAt(LocalDateTime now) {
		Objects.requireNonNull(now, "viewedAt");
		return viewedAt != null ? this : new IncidentAcknowledgement(id, incidentId, familyMemberId,
				now, acknowledgedAt, responseNote, createdAt);
	}

	public IncidentAcknowledgement acknowledgeAt(LocalDateTime now, String note) {
		Objects.requireNonNull(now, "acknowledgedAt");
		return acknowledgedAt != null ? this : new IncidentAcknowledgement(id, incidentId, familyMemberId,
				viewedAt, now, note, createdAt);
	}
}
