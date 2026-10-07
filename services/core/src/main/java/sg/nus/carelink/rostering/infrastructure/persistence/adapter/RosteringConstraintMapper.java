package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import sg.nus.carelink.rostering.domain.model.RosteringConstraint;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringConstraintJpaEntity;

/**
 * JPA entity <-> domain model for rostering_constraint, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by RosteringConstraintMapperTest.
 */
final class RosteringConstraintMapper {

	private RosteringConstraintMapper() {
	}

	static RosteringConstraint toDomain(RosteringConstraintJpaEntity e) {
		return new RosteringConstraint(
				e.getId(),
				e.getCode(),
				e.getName(),
				e.getKind() == null ? null : RosteringConstraint.Kind.valueOf(e.getKind().name()),
				e.getParameterValue(),
				e.isEnabled());
	}

	static RosteringConstraintJpaEntity toEntity(RosteringConstraint d) {
		RosteringConstraintJpaEntity e = new RosteringConstraintJpaEntity();
		e.setId(d.id());
		e.setCode(d.code());
		e.setName(d.name());
		e.setKind(d.kind() == null ? null : RosteringConstraintJpaEntity.Kind.valueOf(d.kind().name()));
		e.setParameterValue(d.parameterValue());
		e.setEnabled(d.enabled());
		return e;
	}
}
