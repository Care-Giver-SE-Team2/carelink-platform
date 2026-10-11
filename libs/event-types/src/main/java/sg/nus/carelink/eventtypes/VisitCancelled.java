package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;

/**
 * A visit became CANCELLED.
 *
 * @param caregiverId the caregiver still named on the visit, if any
 * @param reason {@code PLAN_CHANGED} (the plan changed or stopped), {@code ABSENCE} (called off for an
 *     absence, to be skipped or moved) or {@code CALLED_OFF} (called off for another reason, such as a
 *     withdrawn extra service)
 * @param note the reason given for calling it off
 */
public record VisitCancelled(Long visitId, Long elderId, OffsetDateTime scheduledStart, Long caregiverId,
		String reason, Long absenceId, String note, Integer version, OffsetDateTime cancelledAt) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "VisitCancelled";

}
