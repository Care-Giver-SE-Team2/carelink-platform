package sg.nus.carelink.report.infrastructure.persistence.adapter;

import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.infrastructure.persistence.entity.ValueAddedServiceJpaEntity;

/**
 * JPA entity <-> domain model for value_added_service, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by ValueAddedServiceMapperTest.
 */
final class ValueAddedServiceMapper {

	private ValueAddedServiceMapper() {
	}

	static ValueAddedService toDomain(ValueAddedServiceJpaEntity e) {
		return new ValueAddedService(
				e.getId(),
				e.getName(),
				e.getDescription(),
				e.getStatus() == null ? null : ValueAddedService.Status.valueOf(e.getStatus().name()),
				e.getCreatedAt(),
				e.getUpdatedAt());
	}

	static ValueAddedServiceJpaEntity toEntity(ValueAddedService d) {
		ValueAddedServiceJpaEntity e = new ValueAddedServiceJpaEntity();
		e.setId(d.id());
		e.setName(d.name());
		e.setDescription(d.description());
		e.setStatus(d.status() == null ? null : ValueAddedServiceJpaEntity.Status.valueOf(d.status().name()));
		return e;
	}
}
