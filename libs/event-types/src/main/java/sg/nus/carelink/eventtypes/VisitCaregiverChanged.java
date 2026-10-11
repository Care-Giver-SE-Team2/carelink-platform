package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;

/**
 * A caregiver took a visit over: covered from the plan, or handed over because of an absence. After
 * it the visit is SCHEDULED, with this caregiver. A visit an absence leaves with nobody is
 * {@link VisitExceptionRaised}, cause {@code ABSENCE_UNCOVERED}.
 *
 * @param previousCaregiverId null when the visit had nobody
 * @param absenceId the absence that caused the change, if one did
 */
public record VisitCaregiverChanged(Long visitId, Long elderId, OffsetDateTime scheduledStart, Long caregiverId,
		Long previousCaregiverId, Long absenceId, Integer version, OffsetDateTime changedAt) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "VisitCaregiverChanged";

}
