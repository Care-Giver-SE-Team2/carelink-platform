package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;

/**
 * A roster change, the plan for a visit an absence vacates, was created or settled. It carries
 * the change's whole state. Published by core (rostering) in the transaction that saves it; the
 * envelope's {@code sequence} orders two changes to one roster change.
 *
 * @param visitStart the visit's start when the change was created
 * @param status {@code AWAITING_FAMILY}, {@code UNCOVERED} or {@code RESOLVED}
 * @param outcome {@code REPLACED}, {@code RESCHEDULED}, {@code SKIPPED} or {@code WITHDRAWN}; null until settled
 * @param decidedBy {@code FAMILY}, {@code DEFAULT_PLAN} or {@code MANAGER}; null until settled
 */
public record RosterChangeUpdated(Long rosterChangeId, Long visitId, Long elderId, Long absenceId,
		OffsetDateTime visitStart, Long originalCaregiverId, String status, String outcome, String decidedBy,
		Long assignedCaregiverId, OffsetDateTime changedAt) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "RosterChangeUpdated";

}
