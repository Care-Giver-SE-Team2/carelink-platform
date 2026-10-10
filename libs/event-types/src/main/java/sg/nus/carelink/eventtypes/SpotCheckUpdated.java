package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;

/**
 * A spot check changed: requested, decided, moved, concluded, missed, answered or withdrawn. It
 * carries the spot check's whole state after the change. Published by core (incident) in the
 * transaction that saves it; the envelope's {@code sequence} orders two changes to one spot check.
 *
 * @param approvalStatus {@code PENDING_APPROVAL}, {@code APPROVED} or {@code REJECTED}
 * @param result {@code MEETS_STANDARD} or {@code NEEDS_IMPROVEMENT} once concluded; otherwise null
 * @param outcome {@code COMPLETED}, {@code CAREGIVER_NO_SHOW} or {@code WITHDRAWN}; null while it is open
 */
public record SpotCheckUpdated(Long spotCheckId, Long elderId, Long visitId, Long caregiverId,
		OffsetDateTime proposedTime, String approvalStatus, String result, String outcome, String finding,
		String caregiverResponse, OffsetDateTime checkedAt, OffsetDateTime changedAt) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "SpotCheckUpdated";

}
