package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.domain.model.VisitStateTransition;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitStateTransitionJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class VisitStateTransitionMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		VisitStateTransitionJpaEntity entity = new VisitStateTransitionJpaEntity();
		entity.setId(1L);
		entity.setVisitId(2L);
		entity.setFromState("v3");
		entity.setToState("v4");
		entity.setActorUserId(5L);
		entity.setResult(VisitStateTransitionJpaEntity.Result.APPLIED);
		entity.setRejectionReason("v7");
		entity.setOccurredAt(LocalDateTime.of(2026, 9, 6, 10, 8));

		VisitStateTransition domain = VisitStateTransitionMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.visitId()).isEqualTo(entity.getVisitId());
		assertThat(domain.fromState()).isEqualTo(entity.getFromState());
		assertThat(domain.toState()).isEqualTo(entity.getToState());
		assertThat(domain.actorUserId()).isEqualTo(entity.getActorUserId());
		assertThat(domain.result().name()).isEqualTo(entity.getResult().name());
		assertThat(domain.rejectionReason()).isEqualTo(entity.getRejectionReason());
		assertThat(domain.occurredAt()).isEqualTo(entity.getOccurredAt());

		VisitStateTransitionJpaEntity back = VisitStateTransitionMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getVisitId()).isEqualTo(entity.getVisitId());
		assertThat(back.getFromState()).isEqualTo(entity.getFromState());
		assertThat(back.getToState()).isEqualTo(entity.getToState());
		assertThat(back.getActorUserId()).isEqualTo(entity.getActorUserId());
		assertThat(back.getResult()).isEqualTo(entity.getResult());
		assertThat(back.getRejectionReason()).isEqualTo(entity.getRejectionReason());
		assertThat(back.getOccurredAt()).isEqualTo(entity.getOccurredAt());
	}
}
