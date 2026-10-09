package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.IncidentLog;
import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentLogJpaEntity;
import sg.nus.carelink.incident.infrastructure.persistence.repository.IncidentLogJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class IncidentLogRepositoryAdapterTest {

	private final IncidentLogJpaRepository jpa = mock(IncidentLogJpaRepository.class);
	private final IncidentLogRepositoryAdapter adapter = new IncidentLogRepositoryAdapter(jpa);

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
	void saveGoesThroughSpringDataAndComesBackAsDomain() {
		IncidentLogJpaEntity entity = new IncidentLogJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(IncidentLogJpaEntity.class))).thenReturn(entity);

		IncidentLog saved = adapter.save(IncidentLogMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
