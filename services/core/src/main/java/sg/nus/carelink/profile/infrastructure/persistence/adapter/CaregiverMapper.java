package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.infrastructure.persistence.entity.CaregiverJpaEntity;

/**
 * JPA entity <-> domain model for caregiver, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by CaregiverMapperTest.
 */
final class CaregiverMapper {

	private CaregiverMapper() {
	}

	static Caregiver toDomain(CaregiverJpaEntity e) {
		return new Caregiver(
				e.getId(),
				e.getUserId(),
				e.getFullName(),
				e.getPhone(),
				e.getSector(),
				e.getDialects(),
				e.getStatus() == null ? null : Caregiver.Status.valueOf(e.getStatus().name()),
				e.getCreatedAt(),
				e.getUpdatedAt());
	}

	static CaregiverJpaEntity toEntity(Caregiver d) {
		CaregiverJpaEntity e = new CaregiverJpaEntity();
		e.setId(d.id());
		e.setUserId(d.userId());
		e.setFullName(d.fullName());
		e.setPhone(d.phone());
		e.setSector(d.sector());
		e.setDialects(d.dialects());
		e.setStatus(d.status() == null ? null : CaregiverJpaEntity.Status.valueOf(d.status().name()));
		return e;
	}
}
