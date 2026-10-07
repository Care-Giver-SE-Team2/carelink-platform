package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.infrastructure.persistence.entity.ElderJpaEntity;

/**
 * JPA entity <-> domain model for elder, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by ElderMapperTest.
 */
final class ElderMapper {

	private ElderMapper() {
	}

	static Elder toDomain(ElderJpaEntity e) {
		return new Elder(
				e.getId(),
				e.getUserId(),
				e.getFullName(),
				e.getGender() == null ? null : Elder.Gender.valueOf(e.getGender().name()),
				e.getDateOfBirth(),
				e.getPhone(),
				e.getAddress(),
				e.getPostalCode(),
				e.getSector(),
				e.getPreferredDialects(),
				e.getLivesAlone(),
				e.getMobilityLevel() == null ? null : Elder.MobilityLevel.valueOf(e.getMobilityLevel().name()),
				e.getContinuityPreference() == null ? null : Elder.ContinuityPreference.valueOf(e.getContinuityPreference().name()),
				e.getMedicalNotes(),
				e.getCreatedAt(),
				e.getUpdatedAt());
	}

	static ElderJpaEntity toEntity(Elder d) {
		ElderJpaEntity e = new ElderJpaEntity();
		e.setId(d.id());
		e.setUserId(d.userId());
		e.setFullName(d.fullName());
		e.setGender(d.gender() == null ? null : ElderJpaEntity.Gender.valueOf(d.gender().name()));
		e.setDateOfBirth(d.dateOfBirth());
		e.setPhone(d.phone());
		e.setAddress(d.address());
		e.setPostalCode(d.postalCode());
		e.setSector(d.sector());
		e.setPreferredDialects(d.preferredDialects());
		e.setLivesAlone(d.livesAlone());
		e.setMobilityLevel(d.mobilityLevel() == null ? null : ElderJpaEntity.MobilityLevel.valueOf(d.mobilityLevel().name()));
		e.setContinuityPreference(d.continuityPreference() == null ? null : ElderJpaEntity.ContinuityPreference.valueOf(d.continuityPreference().name()));
		e.setMedicalNotes(d.medicalNotes());
		return e;
	}
}
