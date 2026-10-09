package sg.nus.carelink.incident.domain.model;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Stable care facts shared with incident observers. @author Wang Zhili */
public record FamilyAlertEvent(UUID eventId, Type type, Long incidentId, Long elderId, OffsetDateTime occurredAt) {

	public enum Type { INCIDENT_RAISED, INCIDENT_UNRESOLVED }

	public FamilyAlertEvent {
		Objects.requireNonNull(eventId, "eventId");
		Objects.requireNonNull(type, "type");
		if (incidentId == null || incidentId <= 0 || elderId == null || elderId <= 0) {
			throw new IllegalArgumentException("Positive incident and elder identifiers are required");
		}
		occurredAt = Objects.requireNonNull(occurredAt, "occurredAt")
				.withOffsetSameInstant(ZoneOffset.ofHours(8)).truncatedTo(ChronoUnit.MICROS);
	}
}
