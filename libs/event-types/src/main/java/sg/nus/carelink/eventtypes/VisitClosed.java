package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;

/**
 * A completed visit was closed: nothing more is expected to happen to it.
 *
 * @param status {@code VERIFIED} when the elder confirmed it took place, or {@code AUTO_CLOSED} when the elder
 *        never answered
 */
public record VisitClosed(Long visitId, Long elderId, OffsetDateTime scheduledStart, Long caregiverId, String status,
		Integer version, OffsetDateTime closedAt) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "VisitClosed";

}
