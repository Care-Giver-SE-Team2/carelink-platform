package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import sg.nus.carelink.visit.domain.model.VisitTask;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitTaskJpaEntity;

/**
 * JPA entity <-> domain model for visit_task, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by VisitTaskMapperTest.
 */
final class VisitTaskMapper {

	private VisitTaskMapper() {
	}

	static VisitTask toDomain(VisitTaskJpaEntity e) {
		return new VisitTask(
				e.getId(),
				e.getVisitId(),
				e.getCarePlanNodeId(),
				e.getName(),
				e.getStatus() == null ? null : VisitTask.Status.valueOf(e.getStatus().name()),
				e.getOutcome(),
				e.getCaregiverNote(),
				e.getCompletedAt());
	}

	static VisitTaskJpaEntity toEntity(VisitTask d) {
		VisitTaskJpaEntity e = new VisitTaskJpaEntity();
		e.setId(d.id());
		e.setVisitId(d.visitId());
		e.setCarePlanNodeId(d.carePlanNodeId());
		e.setName(d.name());
		e.setStatus(d.status() == null ? null : VisitTaskJpaEntity.Status.valueOf(d.status().name()));
		e.setOutcome(d.outcome());
		e.setCaregiverNote(d.caregiverNote());
		e.setCompletedAt(d.completedAt());
		return e;
	}
}
