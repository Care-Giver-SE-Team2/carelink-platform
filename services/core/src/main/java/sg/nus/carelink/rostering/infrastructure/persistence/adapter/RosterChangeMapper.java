package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosterChangeJpaEntity;

/** JPA entity <-> domain model for roster_change, both directions, column by column. Covered by RosterChangeMapperTest. */
final class RosterChangeMapper {

	private RosterChangeMapper() {
	}

	static RosterChange toDomain(RosterChangeJpaEntity e) {
		return new RosterChange(
				e.getId(),
				e.getAbsenceId(),
				e.getVisitId(),
				e.getElderId(),
				e.getOriginalCaregiverId(),
				e.getVisitStart(),
				e.getVisitEnd(),
				e.getRosteringRunId(),
				e.getProposedCaregiverId(),
				RosterChange.Status.valueOf(e.getStatus().name()),
				e.getOutcome() == null ? null : RosterChange.Outcome.valueOf(e.getOutcome().name()),
				e.getDecidedBy() == null ? null : RosterChange.DecidedBy.valueOf(e.getDecidedBy().name()),
				e.getDecidedByUserId(),
				e.getAssignedCaregiverId(),
				e.getRescheduledVisitId(),
				e.getIncidentId(),
				e.getRespondBy(),
				e.getDecidedAt(),
				e.getNote(),
				e.getCreatedAt(),
				e.getUpdatedAt());
	}

	static RosterChangeJpaEntity toEntity(RosterChange d) {
		RosterChangeJpaEntity e = new RosterChangeJpaEntity();
		e.setId(d.id());
		e.setAbsenceId(d.absenceId());
		e.setVisitId(d.visitId());
		e.setElderId(d.elderId());
		e.setOriginalCaregiverId(d.originalCaregiverId());
		e.setVisitStart(d.visitStart());
		e.setVisitEnd(d.visitEnd());
		e.setRosteringRunId(d.rosteringRunId());
		e.setProposedCaregiverId(d.proposedCaregiverId());
		e.setStatus(RosterChangeJpaEntity.Status.valueOf(d.status().name()));
		e.setOutcome(d.outcome() == null ? null : RosterChangeJpaEntity.Outcome.valueOf(d.outcome().name()));
		e.setDecidedBy(d.decidedBy() == null ? null : RosterChangeJpaEntity.DecidedBy.valueOf(d.decidedBy().name()));
		e.setDecidedByUserId(d.decidedByUserId());
		e.setAssignedCaregiverId(d.assignedCaregiverId());
		e.setRescheduledVisitId(d.rescheduledVisitId());
		e.setIncidentId(d.incidentId());
		e.setRespondBy(d.respondBy());
		e.setDecidedAt(d.decidedAt());
		e.setNote(d.note());
		e.setCreatedAt(d.createdAt());
		e.setUpdatedAt(d.updatedAt());
		return e;
	}
}
