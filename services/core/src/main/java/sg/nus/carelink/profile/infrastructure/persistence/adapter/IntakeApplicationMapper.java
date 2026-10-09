package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.infrastructure.persistence.entity.IntakeApplicationJpaEntity;

/**
 * JPA entity <-> domain model for intake_application, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by IntakeApplicationMapperTest.
 */
final class IntakeApplicationMapper {

	private IntakeApplicationMapper() {
	}

	static IntakeApplication toDomain(IntakeApplicationJpaEntity e) {
		return new IntakeApplication(
				e.getId(),
				e.getApplicantFamilyMemberId(),
				e.getTargetElderName(),
				e.getTargetElderAge(),
				e.getTargetAddress(),
				e.getPostalCode(),
				e.getMobilityLevel() == null ? null : IntakeApplication.MobilityLevel.valueOf(e.getMobilityLevel().name()),
				e.getPreferredDialects(),
				e.getCareNeeds(),
				e.getMedicalNotes(),
				e.getStatus() == null ? null : IntakeApplication.Status.valueOf(e.getStatus().name()),
				e.getReviewedByUserId(),
				e.getReviewRemarks(),
				e.getCreatedAt(),
				e.getReviewedAt(),
				e.getElderId());
	}

	static IntakeApplicationJpaEntity toEntity(IntakeApplication d) {
		IntakeApplicationJpaEntity e = new IntakeApplicationJpaEntity();
		e.setId(d.id());
		e.setApplicantFamilyMemberId(d.applicantFamilyMemberId());
		e.setTargetElderName(d.targetElderName());
		e.setTargetElderAge(d.targetElderAge());
		e.setTargetAddress(d.targetAddress());
		e.setPostalCode(d.postalCode());
		e.setMobilityLevel(d.mobilityLevel() == null ? null : IntakeApplicationJpaEntity.MobilityLevel.valueOf(d.mobilityLevel().name()));
		e.setPreferredDialects(d.preferredDialects());
		e.setCareNeeds(d.careNeeds());
		e.setMedicalNotes(d.medicalNotes());
		e.setStatus(d.status() == null ? null : IntakeApplicationJpaEntity.Status.valueOf(d.status().name()));
		e.setReviewedByUserId(d.reviewedByUserId());
		e.setReviewRemarks(d.reviewRemarks());
		e.setReviewedAt(d.reviewedAt());
		e.setElderId(d.elderId());
		return e;
	}
}
