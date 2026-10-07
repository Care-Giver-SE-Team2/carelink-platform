package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitJpaEntity;

/**
 * JPA entity <-> domain model for visit, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by VisitMapperTest.
 */
final class VisitMapper {

	private VisitMapper() {
	}

	static Visit toDomain(VisitJpaEntity e) {
		return new Visit(
				e.getId(),
				e.getElderId(),
				e.getCaregiverId(),
				e.getCarePlanNodeId(),
				e.getAbsenceId(),
				e.getServiceType(),
				e.getScheduledStart(),
				e.getScheduledEnd(),
				e.getCheckedInAt(),
				e.getCheckedOutAt(),
				e.getStatus() == null ? null : Visit.Status.valueOf(e.getStatus().name()),
				e.getStateDeadline(),
				e.getCarePlanId(),
				e.getVersion(),
				e.getCreatedAt(),
				e.getUpdatedAt());
	}

	static VisitJpaEntity toEntity(Visit d) {
		VisitJpaEntity e = new VisitJpaEntity();
		e.setId(d.id());
		e.setElderId(d.elderId());
		e.setCaregiverId(d.caregiverId());
		e.setCarePlanNodeId(d.carePlanNodeId());
		e.setAbsenceId(d.absenceId());
		e.setServiceType(d.serviceType());
		e.setScheduledStart(d.scheduledStart());
		e.setScheduledEnd(d.scheduledEnd());
		e.setCheckedInAt(d.checkedInAt());
		e.setCheckedOutAt(d.checkedOutAt());
		e.setStatus(d.status() == null ? null : VisitJpaEntity.Status.valueOf(d.status().name()));
		e.setStateDeadline(d.stateDeadline());
		e.setCarePlanId(d.carePlanId());
		e.setVersion(d.version());
		return e;
	}
}
