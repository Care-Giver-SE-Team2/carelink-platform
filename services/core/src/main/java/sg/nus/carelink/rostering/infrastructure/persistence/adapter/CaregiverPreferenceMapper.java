package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import sg.nus.carelink.rostering.domain.model.CaregiverPreference;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.CaregiverPreferenceJpaEntity;

/**
 * JPA entity <-> domain model for caregiver_preference, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by CaregiverPreferenceMapperTest.
 */
final class CaregiverPreferenceMapper {

	private CaregiverPreferenceMapper() {
	}

	static CaregiverPreference toDomain(CaregiverPreferenceJpaEntity e) {
		return new CaregiverPreference(
				e.getId(),
				e.getCaregiverId(),
				e.getPreferredServiceTypes(),
				e.getPreferredSectors(),
				e.getPreferredTimeWindows(),
				e.getMaxVisitsPerDay(),
				e.getMaxHoursPerDay(),
				e.getUpdatedAt());
	}

	static CaregiverPreferenceJpaEntity toEntity(CaregiverPreference d) {
		CaregiverPreferenceJpaEntity e = new CaregiverPreferenceJpaEntity();
		e.setId(d.id());
		e.setCaregiverId(d.caregiverId());
		e.setPreferredServiceTypes(d.preferredServiceTypes());
		e.setPreferredSectors(d.preferredSectors());
		e.setPreferredTimeWindows(d.preferredTimeWindows());
		e.setMaxVisitsPerDay(d.maxVisitsPerDay());
		e.setMaxHoursPerDay(d.maxHoursPerDay());
		return e;
	}
}
