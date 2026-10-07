package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import sg.nus.carelink.visit.domain.model.VitalSign;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VitalSignJpaEntity;

/**
 * JPA entity <-> domain model for vital_sign, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by VitalSignMapperTest.
 */
final class VitalSignMapper {

	private VitalSignMapper() {
	}

	static VitalSign toDomain(VitalSignJpaEntity e) {
		return new VitalSign(
				e.getId(),
				e.getVisitId(),
				e.getMetric(),
				e.getValue(),
				e.getUnit(),
				e.isOutOfRange(),
				e.getRecordedAt());
	}

	static VitalSignJpaEntity toEntity(VitalSign d) {
		VitalSignJpaEntity e = new VitalSignJpaEntity();
		e.setId(d.id());
		e.setVisitId(d.visitId());
		e.setMetric(d.metric());
		e.setValue(d.value());
		e.setUnit(d.unit());
		e.setOutOfRange(d.outOfRange());
		e.setRecordedAt(d.recordedAt());
		return e;
	}
}
