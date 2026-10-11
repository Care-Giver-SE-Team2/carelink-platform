package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.domain.model.VitalSign;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VitalSignJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class VitalSignMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		VitalSignJpaEntity entity = new VitalSignJpaEntity();
		entity.setId(1L);
		entity.setVisitId(2L);
		entity.setMetric("v3");
		entity.setValue(new BigDecimal("4.5"));
		entity.setUnit("v5");
		entity.setOutOfRange(true);
		entity.setRecordedAt(LocalDateTime.of(2026, 9, 6, 10, 7));

		VitalSign domain = VitalSignMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.visitId()).isEqualTo(entity.getVisitId());
		assertThat(domain.metric()).isEqualTo(entity.getMetric());
		assertThat(domain.value()).isEqualTo(entity.getValue());
		assertThat(domain.unit()).isEqualTo(entity.getUnit());
		assertThat(domain.outOfRange()).isEqualTo(entity.isOutOfRange());
		assertThat(domain.recordedAt()).isEqualTo(entity.getRecordedAt());

		VitalSignJpaEntity back = VitalSignMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getVisitId()).isEqualTo(entity.getVisitId());
		assertThat(back.getMetric()).isEqualTo(entity.getMetric());
		assertThat(back.getValue()).isEqualTo(entity.getValue());
		assertThat(back.getUnit()).isEqualTo(entity.getUnit());
		assertThat(back.isOutOfRange()).isEqualTo(entity.isOutOfRange());
		assertThat(back.getRecordedAt()).isEqualTo(entity.getRecordedAt());
	}
}
