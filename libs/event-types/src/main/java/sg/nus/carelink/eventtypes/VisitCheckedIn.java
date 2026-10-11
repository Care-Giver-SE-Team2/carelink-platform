package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;

/**
 * A caregiver checked in, including a check-in that resumes a visit paused by a missed check-in. After
 * it the visit is IN_PROGRESS.
 */
public record VisitCheckedIn(Long visitId, Long elderId, OffsetDateTime scheduledStart, Long caregiverId,
		OffsetDateTime checkedInAt, boolean resumedAfterMissedCheckIn, Integer version) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "VisitCheckedIn";

}
