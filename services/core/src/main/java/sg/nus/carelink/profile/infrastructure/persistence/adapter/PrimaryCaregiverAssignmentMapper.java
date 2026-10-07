package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import sg.nus.carelink.profile.domain.model.PrimaryCaregiverAssignment;
import sg.nus.carelink.profile.infrastructure.persistence.entity.PrimaryCaregiverAssignmentJpaEntity;

/** JPA entity <-> domain model for elder_primary_caregiver. Covered by PrimaryCaregiverAssignmentMapperTest. */
final class PrimaryCaregiverAssignmentMapper {

	private PrimaryCaregiverAssignmentMapper() {
	}

	static PrimaryCaregiverAssignment toDomain(PrimaryCaregiverAssignmentJpaEntity e) {
		return new PrimaryCaregiverAssignment(e.getElderId(), e.getCaregiverId(), e.getAssignedAt());
	}

	static PrimaryCaregiverAssignmentJpaEntity toEntity(PrimaryCaregiverAssignment d) {
		PrimaryCaregiverAssignmentJpaEntity e = new PrimaryCaregiverAssignmentJpaEntity();
		e.setElderId(d.elderId());
		e.setCaregiverId(d.caregiverId());
		e.setAssignedAt(d.assignedAt());
		return e;
	}
}
