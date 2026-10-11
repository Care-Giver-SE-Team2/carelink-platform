package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;

/** A visit became COMPLETED: the caregiver checked out. */
public record VisitCompleted(Long visitId, Long elderId, OffsetDateTime scheduledStart, Long caregiverId,
		OffsetDateTime checkedInAt, OffsetDateTime checkedOutAt, Integer version) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "VisitCompleted";

}
