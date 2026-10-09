package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.domain.model.ElderConfirmation;
import sg.nus.carelink.visit.infrastructure.persistence.entity.ElderConfirmationJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class ElderConfirmationMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		ElderConfirmationJpaEntity entity = new ElderConfirmationJpaEntity();
		entity.setId(1L);
		entity.setVisitId(2L);
		entity.setElderId(3L);
		entity.setConfirmationStatus(ElderConfirmationJpaEntity.ConfirmationStatus.CONFIRMED);
		entity.setRating((byte) 5);
		entity.setComment("v6");
		entity.setConfirmedAt(LocalDateTime.of(2026, 9, 6, 10, 7));

		ElderConfirmation domain = ElderConfirmationMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.visitId()).isEqualTo(entity.getVisitId());
		assertThat(domain.elderId()).isEqualTo(entity.getElderId());
		assertThat(domain.confirmationStatus().name()).isEqualTo(entity.getConfirmationStatus().name());
		assertThat(domain.rating()).isEqualTo(entity.getRating());
		assertThat(domain.comment()).isEqualTo(entity.getComment());
		assertThat(domain.confirmedAt()).isEqualTo(entity.getConfirmedAt());

		ElderConfirmationJpaEntity back = ElderConfirmationMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getVisitId()).isEqualTo(entity.getVisitId());
		assertThat(back.getElderId()).isEqualTo(entity.getElderId());
		assertThat(back.getConfirmationStatus()).isEqualTo(entity.getConfirmationStatus());
		assertThat(back.getRating()).isEqualTo(entity.getRating());
		assertThat(back.getComment()).isEqualTo(entity.getComment());
		assertThat(back.getConfirmedAt()).isEqualTo(entity.getConfirmedAt());
	}
}
