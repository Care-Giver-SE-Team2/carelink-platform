package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.CaregiverAvailability;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.CaregiverAvailabilityJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class CaregiverAvailabilityMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		CaregiverAvailabilityJpaEntity entity = new CaregiverAvailabilityJpaEntity();
		entity.setId(1L);
		entity.setCaregiverId(2L);
		entity.setAvailableDate(LocalDate.of(2026, 9, 4));
		entity.setAvailableStart(LocalTime.of(4, 0));
		entity.setAvailableEnd(LocalTime.of(5, 0));
		entity.setStatus(CaregiverAvailabilityJpaEntity.Status.AVAILABLE);

		CaregiverAvailability domain = CaregiverAvailabilityMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.caregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(domain.availableDate()).isEqualTo(entity.getAvailableDate());
		assertThat(domain.availableStart()).isEqualTo(entity.getAvailableStart());
		assertThat(domain.availableEnd()).isEqualTo(entity.getAvailableEnd());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());

		CaregiverAvailabilityJpaEntity back = CaregiverAvailabilityMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getCaregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(back.getAvailableDate()).isEqualTo(entity.getAvailableDate());
		assertThat(back.getAvailableStart()).isEqualTo(entity.getAvailableStart());
		assertThat(back.getAvailableEnd()).isEqualTo(entity.getAvailableEnd());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
	}
}
