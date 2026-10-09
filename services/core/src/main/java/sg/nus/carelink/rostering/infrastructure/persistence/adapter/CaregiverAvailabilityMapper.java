package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import sg.nus.carelink.rostering.domain.model.CaregiverAvailability;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.CaregiverAvailabilityJpaEntity;

/**
 * JPA entity <-> domain model for caregiver_availability, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by CaregiverAvailabilityMapperTest.
 */
final class CaregiverAvailabilityMapper {

	private CaregiverAvailabilityMapper() {
	}

	static CaregiverAvailability toDomain(CaregiverAvailabilityJpaEntity e) {
		return new CaregiverAvailability(
				e.getId(),
				e.getCaregiverId(),
				e.getAvailableDate(),
				e.getAvailableStart(),
				e.getAvailableEnd(),
				e.getStatus() == null ? null : CaregiverAvailability.Status.valueOf(e.getStatus().name()),
				e.getCreatedAt());
	}

	static CaregiverAvailabilityJpaEntity toEntity(CaregiverAvailability d) {
		CaregiverAvailabilityJpaEntity e = new CaregiverAvailabilityJpaEntity();
		e.setId(d.id());
		e.setCaregiverId(d.caregiverId());
		e.setAvailableDate(d.availableDate());
		e.setAvailableStart(d.availableStart());
		e.setAvailableEnd(d.availableEnd());
		e.setStatus(d.status() == null ? null : CaregiverAvailabilityJpaEntity.Status.valueOf(d.status().name()));
		return e;
	}
}
