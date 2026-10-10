package sg.nus.carelink.report.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A family member's periodic review of a caregiver that overlaps the report's period (UC-FM09),
 * with the renewal decision it ended in.
 *
 * <p>A reading of caregiver_review. Scores are 1 to 5; the two partial scores are optional.
 *
 * @param renewalDecision RENEW_CURRENT, REQUEST_CHANGE or CANCEL_SERVICE, as the table holds it
 */
public record ReviewFact(
		Long caregiverId,
		String caregiverName,
		LocalDate periodStart,
		LocalDate periodEnd,
		int overallRating,
		Integer punctualityScore,
		Integer careQualityScore,
		String notes,
		String renewalDecision) {

	public ReviewFact {
		Objects.requireNonNull(caregiverId, "caregiverId");
		Objects.requireNonNull(periodStart, "periodStart");
		Objects.requireNonNull(periodEnd, "periodEnd");
		Objects.requireNonNull(renewalDecision, "renewalDecision");
	}
}
