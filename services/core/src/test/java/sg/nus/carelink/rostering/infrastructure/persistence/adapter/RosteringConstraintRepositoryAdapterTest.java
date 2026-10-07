package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.RosteringConstraint;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringConstraintJpaEntity;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.RosteringConstraintJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class RosteringConstraintRepositoryAdapterTest {

	private final RosteringConstraintJpaRepository jpa = mock(RosteringConstraintJpaRepository.class);
	private final RosteringConstraintRepositoryAdapter adapter = new RosteringConstraintRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		RosteringConstraintJpaEntity entity = new RosteringConstraintJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<RosteringConstraint> found = adapter.findById(7L);

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
		RosteringConstraintJpaEntity entity = new RosteringConstraintJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(RosteringConstraintJpaEntity.class))).thenReturn(entity);

		RosteringConstraint saved = adapter.save(RosteringConstraintMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
