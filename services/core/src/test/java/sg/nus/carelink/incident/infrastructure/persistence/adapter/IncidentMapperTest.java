package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class IncidentMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		IncidentJpaEntity entity = new IncidentJpaEntity();
		entity.setId(1L);
		entity.setElderId(2L);
		entity.setVisitId(3L);
		entity.setReportedByUserId(4L);
		entity.setResponderUserId(5L);
		entity.setSource(IncidentJpaEntity.Source.CAREGIVER);
		entity.setCategory(IncidentJpaEntity.Category.SOS);
		entity.setSeverity(IncidentJpaEntity.Severity.LOW);
		entity.setStatus(IncidentJpaEntity.Status.OPEN);
		entity.setLatitude(new BigDecimal("10.5"));
		entity.setLongitude(new BigDecimal("11.5"));
		entity.setLocationText("v12");
		entity.setDescription("v13");
		entity.setRespondBy(LocalDateTime.of(2026, 9, 6, 10, 14));
		entity.setReportedAt(LocalDateTime.of(2026, 9, 6, 10, 15));
		entity.setResolvedAt(LocalDateTime.of(2026, 9, 6, 10, 16));

		Incident domain = IncidentMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.elderId()).isEqualTo(entity.getElderId());
		assertThat(domain.visitId()).isEqualTo(entity.getVisitId());
		assertThat(domain.reportedByUserId()).isEqualTo(entity.getReportedByUserId());
		assertThat(domain.responderUserId()).isEqualTo(entity.getResponderUserId());
		assertThat(domain.source().name()).isEqualTo(entity.getSource().name());
		assertThat(domain.category().name()).isEqualTo(entity.getCategory().name());
		assertThat(domain.severity().name()).isEqualTo(entity.getSeverity().name());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());
		assertThat(domain.latitude()).isEqualTo(entity.getLatitude());
		assertThat(domain.longitude()).isEqualTo(entity.getLongitude());
		assertThat(domain.locationText()).isEqualTo(entity.getLocationText());
		assertThat(domain.description()).isEqualTo(entity.getDescription());
		assertThat(domain.respondBy()).isEqualTo(entity.getRespondBy());
		assertThat(domain.reportedAt()).isEqualTo(entity.getReportedAt());
		assertThat(domain.resolvedAt()).isEqualTo(entity.getResolvedAt());

		IncidentJpaEntity back = IncidentMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getElderId()).isEqualTo(entity.getElderId());
		assertThat(back.getVisitId()).isEqualTo(entity.getVisitId());
		assertThat(back.getReportedByUserId()).isEqualTo(entity.getReportedByUserId());
		assertThat(back.getResponderUserId()).isEqualTo(entity.getResponderUserId());
		assertThat(back.getSource()).isEqualTo(entity.getSource());
		assertThat(back.getCategory()).isEqualTo(entity.getCategory());
		assertThat(back.getSeverity()).isEqualTo(entity.getSeverity());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
		assertThat(back.getLatitude()).isEqualTo(entity.getLatitude());
		assertThat(back.getLongitude()).isEqualTo(entity.getLongitude());
		assertThat(back.getLocationText()).isEqualTo(entity.getLocationText());
		assertThat(back.getDescription()).isEqualTo(entity.getDescription());
		assertThat(back.getRespondBy()).isEqualTo(entity.getRespondBy());
		assertThat(back.getReportedAt()).isEqualTo(entity.getReportedAt());
		assertThat(back.getResolvedAt()).isEqualTo(entity.getResolvedAt());
	}
}
