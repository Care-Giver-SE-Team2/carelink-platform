package sg.nus.carelink.report.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A visit of the period that its caregiver's absence left without them, and how it was settled
 * (UC-MG04).
 *
 * <p>A reading of roster_change. The states are kept as the text the table holds.
 *
 * @param status    AWAITING_FAMILY, UNCOVERED or RESOLVED
 * @param outcome   REPLACED, RESCHEDULED, SKIPPED or WITHDRAWN once resolved; otherwise null
 * @param decidedBy FAMILY, DEFAULT_PLAN or MANAGER once resolved; otherwise null
 */
public record RosterChangeFact(
		Long visitId,
		LocalDateTime visitStart,
		Long originalCaregiverId,
		String originalCaregiverName,
		String status,
		String outcome,
		String decidedBy,
		Long assignedCaregiverId,
		String assignedCaregiverName) {

	public RosterChangeFact {
		Objects.requireNonNull(visitId, "visitId");
		Objects.requireNonNull(visitStart, "visitStart");
		Objects.requireNonNull(originalCaregiverId, "originalCaregiverId");
		Objects.requireNonNull(status, "status");
	}

	public boolean hasReplacement() {
		return assignedCaregiverId != null;
	}
}
