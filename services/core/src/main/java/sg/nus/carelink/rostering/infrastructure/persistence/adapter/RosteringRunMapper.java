package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringRunJpaEntity;

/**
 * JPA entity <-> domain model for rostering_run, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by RosteringRunMapperTest.
 */
final class RosteringRunMapper {

	private RosteringRunMapper() {
	}

	static RosteringRun toDomain(RosteringRunJpaEntity e) {
		return new RosteringRun(
				e.getId(),
				e.getTriggerType() == null ? null : RosteringRun.TriggerType.valueOf(e.getTriggerType().name()),
				e.getAbsenceId(),
				e.getObjective() == null ? null : RosteringRun.Objective.valueOf(e.getObjective().name()),
				e.getRequestedByUserId(),
				e.getStatus() == null ? null : RosteringRun.Status.valueOf(e.getStatus().name()),
				e.getVisitsTotal(),
				e.getVisitsCovered(),
				e.getContinuityKept(),
				e.getAddedTravelKm(),
				e.getRanAt(),
				e.getCommittedAt());
	}

	static RosteringRunJpaEntity toEntity(RosteringRun d) {
		RosteringRunJpaEntity e = new RosteringRunJpaEntity();
		e.setId(d.id());
		e.setTriggerType(d.triggerType() == null ? null : RosteringRunJpaEntity.TriggerType.valueOf(d.triggerType().name()));
		e.setAbsenceId(d.absenceId());
		e.setObjective(d.objective() == null ? null : RosteringRunJpaEntity.Objective.valueOf(d.objective().name()));
		e.setRequestedByUserId(d.requestedByUserId());
		e.setStatus(d.status() == null ? null : RosteringRunJpaEntity.Status.valueOf(d.status().name()));
		e.setVisitsTotal(d.visitsTotal());
		e.setVisitsCovered(d.visitsCovered());
		e.setContinuityKept(d.continuityKept());
		e.setAddedTravelKm(d.addedTravelKm());
		e.setRanAt(d.ranAt());
		e.setCommittedAt(d.committedAt());
		return e;
	}
}
