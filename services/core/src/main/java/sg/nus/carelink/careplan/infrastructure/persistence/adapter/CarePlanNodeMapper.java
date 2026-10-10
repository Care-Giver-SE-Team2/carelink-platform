package sg.nus.carelink.careplan.infrastructure.persistence.adapter;

import java.time.DayOfWeek;
import java.util.List;

import sg.nus.carelink.careplan.domain.model.CarePlanNode;
import sg.nus.carelink.careplan.domain.model.ScheduledVisit;
import sg.nus.carelink.careplan.infrastructure.persistence.entity.CarePlanNodeJpaEntity;
import sg.nus.carelink.careplan.infrastructure.persistence.entity.CarePlanNodeVisitJpaEntity;

/**
 * JPA entity <-> domain model for care_plan_node and its per-day care_plan_node_visit rows, both
 * directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by CarePlanNodeMapperTest.
 */
final class CarePlanNodeMapper {

	private CarePlanNodeMapper() {
	}

	static CarePlanNode toDomain(CarePlanNodeJpaEntity e) {
		return new CarePlanNode(
				e.getId(),
				e.getCarePlanId(),
				e.getGroupName(),
				e.getActivityCode(),
				e.getName(),
				e.getScheduleDays(),
				e.getDurationPerVisit(),
				e.getWeeklyHours(),
				e.getEvidenceType() == null ? null : CarePlanNode.EvidenceType.valueOf(e.getEvidenceType().name()),
				e.getDisplayOrder(),
				e.getCreatedAt(),
				e.getUpdatedAt(),
				e.getVisits() == null ? List.of() : e.getVisits().stream().map(CarePlanNodeMapper::toDomain).toList());
	}

	static CarePlanNodeJpaEntity toEntity(CarePlanNode d) {
		CarePlanNodeJpaEntity e = new CarePlanNodeJpaEntity();
		e.setId(d.id());
		e.setCarePlanId(d.carePlanId());
		e.setGroupName(d.groupName());
		e.setActivityCode(d.activityCode());
		e.setName(d.name());
		e.setScheduleDays(d.scheduleDays());
		e.setDurationPerVisit(d.durationPerVisit());
		e.setWeeklyHours(d.weeklyHours());
		e.setEvidenceType(d.evidenceType() == null ? null : CarePlanNodeJpaEntity.EvidenceType.valueOf(d.evidenceType().name()));
		e.setDisplayOrder(d.displayOrder());
		e.getVisits().addAll(d.visits().stream().map(CarePlanNodeMapper::toEntity).toList());
		return e;
	}

	/** DayOfWeek.MONDAY <-> Day.MON: both enums run Monday to Sunday, so the ordinal lines up. */
	private static ScheduledVisit toDomain(CarePlanNodeVisitJpaEntity e) {
		return new ScheduledVisit(DayOfWeek.of(e.getDayOfWeek().ordinal() + 1), e.getStartTime(), e.getMinutes());
	}

	private static CarePlanNodeVisitJpaEntity toEntity(ScheduledVisit d) {
		CarePlanNodeVisitJpaEntity e = new CarePlanNodeVisitJpaEntity();
		e.setDayOfWeek(CarePlanNodeVisitJpaEntity.Day.values()[d.day().getValue() - 1]);
		e.setStartTime(d.startTime());
		e.setMinutes(d.minutes());
		return e;
	}
}
