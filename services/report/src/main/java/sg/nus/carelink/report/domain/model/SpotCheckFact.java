package sg.nus.carelink.report.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A home spot check proposed for a moment in the period (UC-MG08), whatever became of it.
 *
 * <p>A reading of spot_check. The states are kept as the text the table holds.
 *
 * @param approvalStatus    PENDING_APPROVAL, APPROVED or REJECTED: the family's consent
 * @param result            MEETS_STANDARD or NEEDS_IMPROVEMENT once concluded; otherwise null
 * @param outcome           COMPLETED, CAREGIVER_NO_SHOW or WITHDRAWN once closed; otherwise null
 * @param finding           what the manager recorded on site, in their own words; may be null
 * @param caregiverResponse the caregiver's answer to the finding; may be null
 */
public record SpotCheckFact(
		Long id,
		Long caregiverId,
		String caregiverName,
		LocalDateTime proposedTime,
		String approvalStatus,
		String result,
		String outcome,
		String finding,
		String caregiverResponse,
		LocalDateTime checkedAt) {

	public SpotCheckFact {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(proposedTime, "proposedTime");
		Objects.requireNonNull(approvalStatus, "approvalStatus");
	}

	public boolean hasCaregiver() {
		return caregiverId != null;
	}
}
