package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.domain.model.VisitStateTransition;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitStateTransitionJpaEntity;
import sg.nus.carelink.visit.infrastructure.persistence.repository.VisitStateTransitionJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class VisitStateTransitionRepositoryAdapterTest {

	private final VisitStateTransitionJpaRepository jpa = mock(VisitStateTransitionJpaRepository.class);
	private final VisitStateTransitionRepositoryAdapter adapter = new VisitStateTransitionRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		VisitStateTransitionJpaEntity entity = new VisitStateTransitionJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<VisitStateTransition> found = adapter.findById(7L);

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
		VisitStateTransitionJpaEntity entity = new VisitStateTransitionJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(VisitStateTransitionJpaEntity.class))).thenReturn(entity);

		VisitStateTransition saved = adapter.save(VisitStateTransitionMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
