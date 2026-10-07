package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.IncidentLog;
import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentLogJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class IncidentLogMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		IncidentLogJpaEntity entity = new IncidentLogJpaEntity();
		entity.setId(1L);
		entity.setIncidentId(2L);
		entity.setActor("v3");
		entity.setAction("v4");
		entity.setDetail("v5");
		entity.setOccurredAt(LocalDateTime.of(2026, 9, 6, 10, 6));

		IncidentLog domain = IncidentLogMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.incidentId()).isEqualTo(entity.getIncidentId());
		assertThat(domain.actor()).isEqualTo(entity.getActor());
		assertThat(domain.action()).isEqualTo(entity.getAction());
		assertThat(domain.detail()).isEqualTo(entity.getDetail());
		assertThat(domain.occurredAt()).isEqualTo(entity.getOccurredAt());

		IncidentLogJpaEntity back = IncidentLogMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getIncidentId()).isEqualTo(entity.getIncidentId());
		assertThat(back.getActor()).isEqualTo(entity.getActor());
		assertThat(back.getAction()).isEqualTo(entity.getAction());
		assertThat(back.getDetail()).isEqualTo(entity.getDetail());
		assertThat(back.getOccurredAt()).isEqualTo(entity.getOccurredAt());
	}
}
