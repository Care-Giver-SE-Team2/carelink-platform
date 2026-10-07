package sg.nus.carelink.careplan.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.infrastructure.persistence.entity.CarePlanJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class CarePlanMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		CarePlanJpaEntity entity = new CarePlanJpaEntity();
		entity.setId(1L);
		entity.setElderId(2L);
		entity.setCreatedByUserId(3L);
		entity.setSupersedesPlanId(4L);
		entity.setVersion(5);
		entity.setStatus(CarePlanJpaEntity.Status.DRAFT);
		entity.setTotalHours(new BigDecimal("7.5"));
		entity.setPublishedAt(LocalDateTime.of(2026, 9, 6, 10, 8));
		entity.setStartDate(LocalDate.of(2026, 4, 1));
		entity.setStopEffectiveDate(LocalDate.of(2026, 9, 22));
		entity.setStopReason("Elder moved away");
		entity.setStoppedByUserId(9L);
		entity.setStoppedAt(LocalDateTime.of(2026, 9, 21, 14, 0));

		CarePlan domain = CarePlanMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.elderId()).isEqualTo(entity.getElderId());
		assertThat(domain.createdByUserId()).isEqualTo(entity.getCreatedByUserId());
		assertThat(domain.supersedesPlanId()).isEqualTo(entity.getSupersedesPlanId());
		assertThat(domain.version()).isEqualTo(entity.getVersion());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());
		assertThat(domain.totalHours()).isEqualTo(entity.getTotalHours());
		assertThat(domain.publishedAt()).isEqualTo(entity.getPublishedAt());
		assertThat(domain.startDate()).isEqualTo(entity.getStartDate());
		assertThat(domain.stopEffectiveDate()).isEqualTo(entity.getStopEffectiveDate());
		assertThat(domain.stopReason()).isEqualTo(entity.getStopReason());
		assertThat(domain.stoppedByUserId()).isEqualTo(entity.getStoppedByUserId());
		assertThat(domain.stoppedAt()).isEqualTo(entity.getStoppedAt());

		CarePlanJpaEntity back = CarePlanMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getElderId()).isEqualTo(entity.getElderId());
		assertThat(back.getCreatedByUserId()).isEqualTo(entity.getCreatedByUserId());
		assertThat(back.getSupersedesPlanId()).isEqualTo(entity.getSupersedesPlanId());
		assertThat(back.getVersion()).isEqualTo(entity.getVersion());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
		assertThat(back.getTotalHours()).isEqualTo(entity.getTotalHours());
		assertThat(back.getPublishedAt()).isEqualTo(entity.getPublishedAt());
		assertThat(back.getStartDate()).isEqualTo(entity.getStartDate());
		assertThat(back.getStopEffectiveDate()).isEqualTo(entity.getStopEffectiveDate());
		assertThat(back.getStopReason()).isEqualTo(entity.getStopReason());
		assertThat(back.getStoppedByUserId()).isEqualTo(entity.getStoppedByUserId());
		assertThat(back.getStoppedAt()).isEqualTo(entity.getStoppedAt());
	}
}
