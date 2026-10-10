package sg.nus.carelink.report.infrastructure.persistence.adapter;

import sg.nus.carelink.report.domain.model.CaregiverReview;
import sg.nus.carelink.report.infrastructure.persistence.entity.CaregiverReviewJpaEntity;

/**
 * JPA entity <-> domain model for caregiver_review, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by CaregiverReviewMapperTest.
 */
final class CaregiverReviewMapper {

	private CaregiverReviewMapper() {
	}

	static CaregiverReview toDomain(CaregiverReviewJpaEntity e) {
		return new CaregiverReview(
				e.getId(),
				e.getFamilyMemberId(),
				e.getElderId(),
				e.getCaregiverId(),
				e.getPeriodStart(),
				e.getPeriodEnd(),
				e.getOverallRating(),
				e.getPunctualityScore(),
				e.getCareQualityScore(),
				e.getFeedbackNotes(),
				e.getRenewalDecision() == null ? null : CaregiverReview.RenewalDecision.valueOf(e.getRenewalDecision().name()),
				e.getCreatedAt());
	}

	static CaregiverReviewJpaEntity toEntity(CaregiverReview d) {
		CaregiverReviewJpaEntity e = new CaregiverReviewJpaEntity();
		e.setId(d.id());
		e.setFamilyMemberId(d.familyMemberId());
		e.setElderId(d.elderId());
		e.setCaregiverId(d.caregiverId());
		e.setPeriodStart(d.periodStart());
		e.setPeriodEnd(d.periodEnd());
		e.setOverallRating(d.overallRating());
		e.setPunctualityScore(d.punctualityScore());
		e.setCareQualityScore(d.careQualityScore());
		e.setFeedbackNotes(d.feedbackNotes());
		e.setRenewalDecision(d.renewalDecision() == null ? null : CaregiverReviewJpaEntity.RenewalDecision.valueOf(d.renewalDecision().name()));
		return e;
	}
}
