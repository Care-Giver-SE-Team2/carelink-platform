package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;

/**
 * An incident was created. Published by core (incident) in the transaction that saves it.
 * Sensitive: the description is the reporter's own words.
 *
 * @param visitId null for an incident with no visit, such as an SOS
 * @param source {@code CAREGIVER}, {@code ELDER_SOS}, {@code ELDER_SERVICE_DISPUTE} or
 * {@code SYSTEM_MISSED_CHECKIN}
 * @param description up to 2000 characters; may be null
 */
public record IncidentRaised(Long incidentId, Long elderId, Long visitId, String source, String category,
		String severity, String status, String description, OffsetDateTime reportedAt) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "IncidentRaised";

}
