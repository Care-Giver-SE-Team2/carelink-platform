package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.infrastructure.persistence.entity.IntakeApplicationJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class IntakeApplicationMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		IntakeApplicationJpaEntity entity = new IntakeApplicationJpaEntity();
		entity.setId(1L);
		entity.setApplicantFamilyMemberId(2L);
		entity.setTargetElderName("v3");
		entity.setTargetElderAge(4);
		entity.setTargetAddress("v5");
		entity.setPostalCode("v6");
		entity.setMobilityLevel(IntakeApplicationJpaEntity.MobilityLevel.INDEPENDENT);
		entity.setPreferredDialects("v8");
		entity.setCareNeeds(List.of("BATHING", "VITALS"));
		entity.setMedicalNotes("v10");
		entity.setStatus(IntakeApplicationJpaEntity.Status.SUBMITTED);
		entity.setReviewedByUserId(12L);
		entity.setReviewRemarks("v13");
		entity.setReviewedAt(LocalDateTime.of(2026, 9, 6, 10, 15));
		entity.setElderId(16L);

		IntakeApplication domain = IntakeApplicationMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.applicantFamilyMemberId()).isEqualTo(entity.getApplicantFamilyMemberId());
		assertThat(domain.targetElderName()).isEqualTo(entity.getTargetElderName());
		assertThat(domain.targetElderAge()).isEqualTo(entity.getTargetElderAge());
		assertThat(domain.targetAddress()).isEqualTo(entity.getTargetAddress());
		assertThat(domain.postalCode()).isEqualTo(entity.getPostalCode());
		assertThat(domain.mobilityLevel().name()).isEqualTo(entity.getMobilityLevel().name());
		assertThat(domain.preferredDialects()).isEqualTo(entity.getPreferredDialects());
		assertThat(domain.careNeeds()).isEqualTo(entity.getCareNeeds());
		assertThat(domain.medicalNotes()).isEqualTo(entity.getMedicalNotes());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());
		assertThat(domain.reviewedByUserId()).isEqualTo(entity.getReviewedByUserId());
		assertThat(domain.reviewRemarks()).isEqualTo(entity.getReviewRemarks());
		assertThat(domain.reviewedAt()).isEqualTo(entity.getReviewedAt());
		assertThat(domain.elderId()).isEqualTo(entity.getElderId());

		IntakeApplicationJpaEntity back = IntakeApplicationMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getApplicantFamilyMemberId()).isEqualTo(entity.getApplicantFamilyMemberId());
		assertThat(back.getTargetElderName()).isEqualTo(entity.getTargetElderName());
		assertThat(back.getTargetElderAge()).isEqualTo(entity.getTargetElderAge());
		assertThat(back.getTargetAddress()).isEqualTo(entity.getTargetAddress());
		assertThat(back.getPostalCode()).isEqualTo(entity.getPostalCode());
		assertThat(back.getMobilityLevel()).isEqualTo(entity.getMobilityLevel());
		assertThat(back.getPreferredDialects()).isEqualTo(entity.getPreferredDialects());
		assertThat(back.getCareNeeds()).isEqualTo(entity.getCareNeeds());
		assertThat(back.getMedicalNotes()).isEqualTo(entity.getMedicalNotes());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
		assertThat(back.getReviewedByUserId()).isEqualTo(entity.getReviewedByUserId());
		assertThat(back.getReviewRemarks()).isEqualTo(entity.getReviewRemarks());
		assertThat(back.getReviewedAt()).isEqualTo(entity.getReviewedAt());
		assertThat(back.getElderId()).isEqualTo(entity.getElderId());
	}
}
