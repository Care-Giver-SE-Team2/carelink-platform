package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;

/**
 * An entry was added to an incident's timeline, including the first one ({@code REPORTED}).
 * Published by core (incident), one event per entry, in the transaction that adds it. Sensitive:
 * the detail holds a manager's notes.
 *
 * @param logId the entry's id; it increases within an incident, so it orders the events
 * @param actor a username, or {@code system}
 * @param detail on {@code RESOLVED}, the outcome code, {@code " :: "} and the manager's note; may be null
 * @param occurredAt when the entry happened, on Singapore's clock
 * @param status the incident's status as of this entry
 * @param severity the incident's severity as of this entry
 * @param resolvedAt null until the incident is resolved
 */
public record IncidentUpdated(Long incidentId, Long elderId, Long logId, String action, String actor, String detail,
		OffsetDateTime occurredAt, String status, String severity, OffsetDateTime resolvedAt) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "IncidentUpdated";

}
