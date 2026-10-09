package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.IncidentAcknowledgement;
import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentAcknowledgementJpaEntity;
import sg.nus.carelink.incident.infrastructure.persistence.repository.IncidentAcknowledgementJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class IncidentAcknowledgementRepositoryAdapterTest {

	private final IncidentAcknowledgementJpaRepository jpa = mock(IncidentAcknowledgementJpaRepository.class);
	private final IncidentAcknowledgementRepositoryAdapter adapter = new IncidentAcknowledgementRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		IncidentAcknowledgementJpaEntity entity = new IncidentAcknowledgementJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<IncidentAcknowledgement> found = adapter.findById(7L);

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
		IncidentAcknowledgementJpaEntity entity = new IncidentAcknowledgementJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(IncidentAcknowledgementJpaEntity.class))).thenReturn(entity);

		IncidentAcknowledgement saved = adapter.save(IncidentAcknowledgementMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
