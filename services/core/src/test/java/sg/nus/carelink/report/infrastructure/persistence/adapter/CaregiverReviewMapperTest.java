package sg.nus.carelink.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.domain.model.CaregiverReview;
import sg.nus.carelink.report.infrastructure.persistence.entity.CaregiverReviewJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class CaregiverReviewMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		CaregiverReviewJpaEntity entity = new CaregiverReviewJpaEntity();
		entity.setId(1L);
		entity.setFamilyMemberId(2L);
		entity.setElderId(3L);
		entity.setCaregiverId(4L);
		entity.setPeriodStart(LocalDate.of(2026, 9, 6));
		entity.setPeriodEnd(LocalDate.of(2026, 9, 7));
		entity.setOverallRating((byte) 7);
		entity.setPunctualityScore((byte) 8);
		entity.setCareQualityScore((byte) 9);
		entity.setFeedbackNotes("v10");
		entity.setRenewalDecision(CaregiverReviewJpaEntity.RenewalDecision.RENEW_CURRENT);

		CaregiverReview domain = CaregiverReviewMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.familyMemberId()).isEqualTo(entity.getFamilyMemberId());
		assertThat(domain.elderId()).isEqualTo(entity.getElderId());
		assertThat(domain.caregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(domain.periodStart()).isEqualTo(entity.getPeriodStart());
		assertThat(domain.periodEnd()).isEqualTo(entity.getPeriodEnd());
		assertThat(domain.overallRating()).isEqualTo(entity.getOverallRating());
		assertThat(domain.punctualityScore()).isEqualTo(entity.getPunctualityScore());
		assertThat(domain.careQualityScore()).isEqualTo(entity.getCareQualityScore());
		assertThat(domain.feedbackNotes()).isEqualTo(entity.getFeedbackNotes());
		assertThat(domain.renewalDecision().name()).isEqualTo(entity.getRenewalDecision().name());

		CaregiverReviewJpaEntity back = CaregiverReviewMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getFamilyMemberId()).isEqualTo(entity.getFamilyMemberId());
		assertThat(back.getElderId()).isEqualTo(entity.getElderId());
		assertThat(back.getCaregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(back.getPeriodStart()).isEqualTo(entity.getPeriodStart());
		assertThat(back.getPeriodEnd()).isEqualTo(entity.getPeriodEnd());
		assertThat(back.getOverallRating()).isEqualTo(entity.getOverallRating());
		assertThat(back.getPunctualityScore()).isEqualTo(entity.getPunctualityScore());
		assertThat(back.getCareQualityScore()).isEqualTo(entity.getCareQualityScore());
		assertThat(back.getFeedbackNotes()).isEqualTo(entity.getFeedbackNotes());
		assertThat(back.getRenewalDecision()).isEqualTo(entity.getRenewalDecision());
	}
}
