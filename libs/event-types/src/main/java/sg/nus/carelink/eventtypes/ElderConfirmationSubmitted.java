package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;

/**
 * The elder confirmed or disputed a visit.
 *
 * @param status {@code CONFIRMED} or {@code DISPUTED}
 * @param rating 1 to 5; may be null
 * @param incidentId the incident a dispute raised
 */
public record ElderConfirmationSubmitted(Long visitId, Long elderId, OffsetDateTime visitScheduledStart, String status,
		Integer rating, String comment, OffsetDateTime confirmedAt, Long incidentId) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "ElderConfirmationSubmitted";

}
