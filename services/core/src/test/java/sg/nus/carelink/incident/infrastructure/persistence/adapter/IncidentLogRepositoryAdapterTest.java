package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.IncidentUpdated;
import sg.nus.carelink.incident.domain.model.IncidentLog;
import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentJpaEntity;
import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentLogJpaEntity;
import sg.nus.carelink.incident.infrastructure.persistence.repository.IncidentJpaRepository;
import sg.nus.carelink.incident.infrastructure.persistence.repository.IncidentLogJpaRepository;

/**
 * The adapter delegates to Spring Data and maps at the boundary, and publishes every entry as
 * {@code IncidentUpdated} with the incident's state.
 */
class IncidentLogRepositoryAdapterTest {

	private final IncidentLogJpaRepository jpa = mock(IncidentLogJpaRepository.class);
	private final IncidentJpaRepository incidents = mock(IncidentJpaRepository.class);
	private final Events events = mock(Events.class);
	private final IncidentLogRepositoryAdapter adapter = new IncidentLogRepositoryAdapter(jpa, incidents, events);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		IncidentLogJpaEntity entity = new IncidentLogJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<IncidentLog> found = adapter.findById(7L);

		assertThat(found).isPresent();
		assertThat(found.get().id()).isEqualTo(7L);
	}

	@Test
	void findByIdIsEmptyWhenThereIsNoRow() {
		when(jpa.findById(any())).thenReturn(Optional.empty());

		assertThat(adapter.findById(7L)).isEmpty();
	}

	@Test
	void anEntryIsSavedAndPublishedWithTheIncidentsState() {
		IncidentLogJpaEntity entity = new IncidentLogJpaEntity();
		entity.setId(9001L);
		entity.setIncidentId(601L);
		entity.setActor("lee.manager");
		entity.setAction("CLAIMED");
		entity.setOccurredAt(LocalDateTime.of(2026, 10, 12, 9, 41));
		when(jpa.save(any(IncidentLogJpaEntity.class))).thenReturn(entity);
		IncidentJpaEntity incident = new IncidentJpaEntity();
		incident.setId(601L);
		incident.setElderId(101L);
		incident.setSource(IncidentJpaEntity.Source.CAREGIVER);
		incident.setCategory(IncidentJpaEntity.Category.FALL);
		incident.setSeverity(IncidentJpaEntity.Severity.HIGH);
		incident.setStatus(IncidentJpaEntity.Status.IN_PROGRESS);
		incident.setReportedAt(LocalDateTime.of(2026, 10, 12, 9, 35));
		when(incidents.findById(601L)).thenReturn(Optional.of(incident));

		IncidentLog saved = adapter.save(IncidentLogMapper.toDomain(entity));

		assertThat(saved.id()).isEqualTo(9001L);
		verify(events).publish(IncidentUpdated.TYPE, new IncidentUpdated(601L, 101L, 9001L, "CLAIMED", "lee.manager",
				null, OffsetDateTime.parse("2026-10-12T09:41:00+08:00"), "IN_PROGRESS", "HIGH", null));
	}
}
