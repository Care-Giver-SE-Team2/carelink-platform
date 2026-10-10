package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.domain.model.VisitTask;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitTaskJpaEntity;
import sg.nus.carelink.visit.infrastructure.persistence.repository.VisitTaskJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class VisitTaskRepositoryAdapterTest {

	private final VisitTaskJpaRepository jpa = mock(VisitTaskJpaRepository.class);
	private final VisitTaskRepositoryAdapter adapter = new VisitTaskRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		VisitTaskJpaEntity entity = new VisitTaskJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<VisitTask> found = adapter.findById(7L);

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
		VisitTaskJpaEntity entity = new VisitTaskJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(VisitTaskJpaEntity.class))).thenReturn(entity);

		VisitTask saved = adapter.save(VisitTaskMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
