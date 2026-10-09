package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.domain.model.VisitAssignment;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitAssignmentJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class VisitAssignmentMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		VisitAssignmentJpaEntity entity = new VisitAssignmentJpaEntity();
		entity.setId(1L);
		entity.setVisitId(2L);
		entity.setCaregiverId(3L);
		entity.setAssignedByUserId(4L);
		entity.setStatus(VisitAssignmentJpaEntity.Status.ACTIVE);
		entity.setReason("v6");
		entity.setAssignedAt(LocalDateTime.of(2026, 9, 6, 10, 7));
		entity.setEndedAt(LocalDateTime.of(2026, 9, 6, 10, 8));
		entity.setRosteringCandidateId(9L);

		VisitAssignment domain = VisitAssignmentMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.visitId()).isEqualTo(entity.getVisitId());
		assertThat(domain.caregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(domain.assignedByUserId()).isEqualTo(entity.getAssignedByUserId());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());
		assertThat(domain.reason()).isEqualTo(entity.getReason());
		assertThat(domain.assignedAt()).isEqualTo(entity.getAssignedAt());
		assertThat(domain.endedAt()).isEqualTo(entity.getEndedAt());
		assertThat(domain.rosteringCandidateId()).isEqualTo(entity.getRosteringCandidateId());

		VisitAssignmentJpaEntity back = VisitAssignmentMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getVisitId()).isEqualTo(entity.getVisitId());
		assertThat(back.getCaregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(back.getAssignedByUserId()).isEqualTo(entity.getAssignedByUserId());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
		assertThat(back.getReason()).isEqualTo(entity.getReason());
		assertThat(back.getAssignedAt()).isEqualTo(entity.getAssignedAt());
		assertThat(back.getEndedAt()).isEqualTo(entity.getEndedAt());
		assertThat(back.getRosteringCandidateId()).isEqualTo(entity.getRosteringCandidateId());
	}
}
