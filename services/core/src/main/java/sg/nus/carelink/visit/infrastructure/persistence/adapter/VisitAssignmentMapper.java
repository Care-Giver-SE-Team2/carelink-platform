package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import sg.nus.carelink.visit.domain.model.VisitAssignment;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitAssignmentJpaEntity;

/**
 * JPA entity <-> domain model for visit_assignment, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by VisitAssignmentMapperTest.
 */
final class VisitAssignmentMapper {

	private VisitAssignmentMapper() {
	}

	static VisitAssignment toDomain(VisitAssignmentJpaEntity e) {
		return new VisitAssignment(
				e.getId(),
				e.getVisitId(),
				e.getCaregiverId(),
				e.getAssignedByUserId(),
				e.getStatus() == null ? null : VisitAssignment.Status.valueOf(e.getStatus().name()),
				e.getReason(),
				e.getAssignedAt(),
				e.getEndedAt(),
				e.getRosteringCandidateId());
	}

	static VisitAssignmentJpaEntity toEntity(VisitAssignment d) {
		VisitAssignmentJpaEntity e = new VisitAssignmentJpaEntity();
		e.setId(d.id());
		e.setVisitId(d.visitId());
		e.setCaregiverId(d.caregiverId());
		e.setAssignedByUserId(d.assignedByUserId());
		e.setStatus(d.status() == null ? null : VisitAssignmentJpaEntity.Status.valueOf(d.status().name()));
		e.setReason(d.reason());
		e.setAssignedAt(d.assignedAt());
		e.setEndedAt(d.endedAt());
		e.setRosteringCandidateId(d.rosteringCandidateId());
		return e;
	}
}
