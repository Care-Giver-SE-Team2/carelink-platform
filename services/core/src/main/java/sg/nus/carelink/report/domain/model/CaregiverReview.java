package sg.nus.carelink.report.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Domain model for caregiver_review.
 *
 * <p>Generated starting point: the same fields as the table, and nothing else. This is
 * where the business rules and the design patterns go — reshape it into a proper
 * aggregate (add behaviour, fold child tables in, drop columns the domain does not
 * care about). identity.domain.model.AppUser is the template. Must not import JPA or
 * Spring Data; ArchUnit rejects the build if it does.
 */
public record CaregiverReview(
		Long id,
		Long familyMemberId,
		Long elderId,
		Long caregiverId,
		LocalDate periodStart,
		LocalDate periodEnd,
		Byte overallRating,
		Byte punctualityScore,
		Byte careQualityScore,
		String feedbackNotes,
		CaregiverReview.RenewalDecision renewalDecision,
		LocalDateTime createdAt) {

	public enum RenewalDecision {
		RENEW_CURRENT, REQUEST_CHANGE, CANCEL_SERVICE
	}
}
