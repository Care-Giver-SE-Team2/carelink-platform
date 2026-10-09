package sg.nus.carelink.incident.domain.model;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * One entry of an incident's timeline.
 *
 * <p>Append-only by design. The use case requires that every step, including the refused
 * and the timed-out ones, can be read back afterwards, so nothing here is ever updated or
 * deleted - a correction is another entry. {@link Action} names the entries the escalation
 * flow writes, so the front end and the report can group them without parsing prose.
 */
public record IncidentLog(
		Long id,
		Long incidentId,
		String actor,
		String action,
		String detail,
		LocalDateTime occurredAt) {

	public static IncidentLog entry(Long incidentId, String actor, Action action, String detail, LocalDateTime at) {
		return new IncidentLog(null, incidentId, actor, action.name(), truncate(detail), at);
	}

	/** The system itself acting, rather than a person: the scheduled escalation scan. */
	public static IncidentLog systemEntry(Long incidentId, Action action, String detail, LocalDateTime at) {
		return entry(incidentId, SYSTEM_ACTOR, action, detail, at);
	}

	/**
	 * Records that the incident was handed to a responder.
	 *
	 * <p>The responder's id is written into the detail in a fixed form because the timeline
	 * is the only record of who has already held this incident, and an escalation must not
	 * hand it back to somebody it has already timed out on. Two managers would otherwise
	 * pass one incident back and forth for ever.
	 *
	 * <p>This is the single machine-readable field in the timeline. Everything after the
	 * separator is prose for a human reader.
	 */
	public static IncidentLog assignment(
			Long incidentId, String actor, Long responderUserId, String narrative, LocalDateTime at) {

		return entry(incidentId, actor, Action.ASSIGNED,
				"%s%d :: %s".formatted(RESPONDER_PREFIX, responderUserId, narrative), at);
	}

	/** The responder an {@link Action#ASSIGNED} entry named, if this is one. */
	public Optional<Long> assignedResponderId() {
		if (!Action.ASSIGNED.name().equals(action) || detail == null || !detail.startsWith(RESPONDER_PREFIX)) {
			return Optional.empty();
		}
		String digits = detail.substring(RESPONDER_PREFIX.length());
		int end = 0;
		while (end < digits.length() && Character.isDigit(digits.charAt(end))) {
			end++;
		}
		if (end == 0) {
			return Optional.empty();
		}
		return Optional.of(Long.parseLong(digits.substring(0, end)));
	}

	public static final String SYSTEM_ACTOR = "system";

	private static final String RESPONDER_PREFIX = "responder=";

	/** incident_log.detail is VARCHAR(500); a long reason is trimmed rather than rejected. */
	private static String truncate(String detail) {
		if (detail == null) {
			return null;
		}
		return detail.length() <= 500 ? detail : detail.substring(0, 497) + "...";
	}

	/** Every kind of entry the incident flow writes. Stored as text in incident_log.action. */
	public enum Action {
		REPORTED,
		BROADCAST,
		ASSIGNED,
		CLAIMED,
		CLAIM_REJECTED,
		ESCALATED,
		ESCALATION_CANCELLED,
		CHAIN_EXHAUSTED,
		CONTACT_ATTEMPTED,
		PLAYBOOK_APPLIED,
		SEVERITY_CHANGED,
		RESOLVED
	}
}
