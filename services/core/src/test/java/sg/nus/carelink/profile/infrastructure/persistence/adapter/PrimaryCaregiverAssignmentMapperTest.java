package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.PrimaryCaregiverAssignment;
import sg.nus.carelink.profile.infrastructure.persistence.entity.PrimaryCaregiverAssignmentJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class PrimaryCaregiverAssignmentMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		PrimaryCaregiverAssignmentJpaEntity entity = new PrimaryCaregiverAssignmentJpaEntity();
		entity.setElderId(1L);
		entity.setCaregiverId(2L);
		entity.setAssignedAt(LocalDateTime.of(2026, 9, 26, 10, 30));

		PrimaryCaregiverAssignment domain = PrimaryCaregiverAssignmentMapper.toDomain(entity);
		assertThat(domain).isEqualTo(new PrimaryCaregiverAssignment(1L, 2L, LocalDateTime.of(2026, 9, 26, 10, 30)));

		PrimaryCaregiverAssignmentJpaEntity back = PrimaryCaregiverAssignmentMapper.toEntity(domain);
		assertThat(back.getElderId()).isEqualTo(entity.getElderId());
		assertThat(back.getCaregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(back.getAssignedAt()).isEqualTo(entity.getAssignedAt());
	}
}
