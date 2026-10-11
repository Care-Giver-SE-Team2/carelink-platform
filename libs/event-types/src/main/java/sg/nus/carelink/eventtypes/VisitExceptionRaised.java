package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;

/**
 * A visit became EXCEPTION.
 *
 * @param caregiverId the caregiver on the visit after the change: null when nobody covers it
 * @param cause {@code CAREGIVER_REPORT}, {@code UNCOVERED_AT_START} or {@code ABSENCE_UNCOVERED}
 * @param absenceId the absence that left the visit with nobody, for {@code ABSENCE_UNCOVERED}
 * @param incidentId the incident the change raised, when it raised one in the same step
 * @param checkedIn whether the caregiver had checked in
 */
public record VisitExceptionRaised(Long visitId, Long elderId, OffsetDateTime scheduledStart, Long caregiverId,
		String cause, Long absenceId, Long incidentId, boolean checkedIn, Integer version) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "VisitExceptionRaised";

}
