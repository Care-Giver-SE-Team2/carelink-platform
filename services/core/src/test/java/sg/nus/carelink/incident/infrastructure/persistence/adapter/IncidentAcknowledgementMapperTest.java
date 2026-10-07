package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.IncidentAcknowledgement;
import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentAcknowledgementJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class IncidentAcknowledgementMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		IncidentAcknowledgementJpaEntity entity = new IncidentAcknowledgementJpaEntity();
		entity.setId(1L);
		entity.setIncidentId(2L);
		entity.setFamilyMemberId(3L);
		entity.setViewedAt(LocalDateTime.of(2026, 9, 6, 10, 4));
		entity.setAcknowledgedAt(LocalDateTime.of(2026, 9, 6, 10, 5));
		entity.setResponseNote("v6");

		IncidentAcknowledgement domain = IncidentAcknowledgementMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.incidentId()).isEqualTo(entity.getIncidentId());
		assertThat(domain.familyMemberId()).isEqualTo(entity.getFamilyMemberId());
		assertThat(domain.viewedAt()).isEqualTo(entity.getViewedAt());
		assertThat(domain.acknowledgedAt()).isEqualTo(entity.getAcknowledgedAt());
		assertThat(domain.responseNote()).isEqualTo(entity.getResponseNote());

		IncidentAcknowledgementJpaEntity back = IncidentAcknowledgementMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getIncidentId()).isEqualTo(entity.getIncidentId());
		assertThat(back.getFamilyMemberId()).isEqualTo(entity.getFamilyMemberId());
		assertThat(back.getViewedAt()).isEqualTo(entity.getViewedAt());
		assertThat(back.getAcknowledgedAt()).isEqualTo(entity.getAcknowledgedAt());
		assertThat(back.getResponseNote()).isEqualTo(entity.getResponseNote());
	}
}
