package sg.nus.carelink.careplan.infrastructure.persistence.adapter;

import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.infrastructure.persistence.entity.CarePlanJpaEntity;

/**
 * JPA entity <-> domain model for care_plan, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by CarePlanMapperTest.
 */
final class CarePlanMapper {

	private CarePlanMapper() {
	}

	static CarePlan toDomain(CarePlanJpaEntity e) {
		return new CarePlan(
				e.getId(),
				e.getElderId(),
				e.getCreatedByUserId(),
				e.getSupersedesPlanId(),
				e.getVersion(),
				e.getStatus() == null ? null : CarePlan.Status.valueOf(e.getStatus().name()),
				e.getTotalHours(),
				e.getPublishedAt(),
				e.getCreatedAt(),
				e.getUpdatedAt(),
				e.getStartDate(),
				e.getStopEffectiveDate(),
				e.getStopReason(),
				e.getStoppedByUserId(),
				e.getStoppedAt());
	}

	static CarePlanJpaEntity toEntity(CarePlan d) {
		CarePlanJpaEntity e = new CarePlanJpaEntity();
		e.setId(d.id());
		e.setElderId(d.elderId());
		e.setCreatedByUserId(d.createdByUserId());
		e.setSupersedesPlanId(d.supersedesPlanId());
		e.setVersion(d.version());
		e.setStatus(d.status() == null ? null : CarePlanJpaEntity.Status.valueOf(d.status().name()));
		e.setTotalHours(d.totalHours());
		e.setPublishedAt(d.publishedAt());
		e.setStartDate(d.startDate());
		e.setStopEffectiveDate(d.stopEffectiveDate());
		e.setStopReason(d.stopReason());
		e.setStoppedByUserId(d.stoppedByUserId());
		e.setStoppedAt(d.stoppedAt());
		return e;
	}
}
