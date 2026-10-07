package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.domain.model.VisitTask;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitTaskJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class VisitTaskMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		VisitTaskJpaEntity entity = new VisitTaskJpaEntity();
		entity.setId(1L);
		entity.setVisitId(2L);
		entity.setCarePlanNodeId(3L);
		entity.setName("v4");
		entity.setStatus(VisitTaskJpaEntity.Status.PENDING);
		entity.setOutcome("v6");
		entity.setCaregiverNote("v7");
		entity.setCompletedAt(LocalDateTime.of(2026, 9, 6, 10, 8));

		VisitTask domain = VisitTaskMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.visitId()).isEqualTo(entity.getVisitId());
		assertThat(domain.carePlanNodeId()).isEqualTo(entity.getCarePlanNodeId());
		assertThat(domain.name()).isEqualTo(entity.getName());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());
		assertThat(domain.outcome()).isEqualTo(entity.getOutcome());
		assertThat(domain.caregiverNote()).isEqualTo(entity.getCaregiverNote());
		assertThat(domain.completedAt()).isEqualTo(entity.getCompletedAt());

		VisitTaskJpaEntity back = VisitTaskMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getVisitId()).isEqualTo(entity.getVisitId());
		assertThat(back.getCarePlanNodeId()).isEqualTo(entity.getCarePlanNodeId());
		assertThat(back.getName()).isEqualTo(entity.getName());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
		assertThat(back.getOutcome()).isEqualTo(entity.getOutcome());
		assertThat(back.getCaregiverNote()).isEqualTo(entity.getCaregiverNote());
		assertThat(back.getCompletedAt()).isEqualTo(entity.getCompletedAt());
	}
}
