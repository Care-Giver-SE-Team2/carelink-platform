package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;

/**
 * A task's result was recorded. Ordered by the envelope's sequence: a task has no version of its own.
 *
 * @param status {@code DONE}, {@code SKIPPED} or {@code REFUSED}
 */
public record VisitTaskRecorded(Long taskId, Long visitId, Long elderId, OffsetDateTime visitScheduledStart, String name,
		String status, String outcome, String caregiverNote, OffsetDateTime recordedAt) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "VisitTaskRecorded";

}
