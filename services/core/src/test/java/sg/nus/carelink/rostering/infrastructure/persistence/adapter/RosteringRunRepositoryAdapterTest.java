package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringRunJpaEntity;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.RosteringRunJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class RosteringRunRepositoryAdapterTest {

	private final RosteringRunJpaRepository jpa = mock(RosteringRunJpaRepository.class);
	private final RosteringRunRepositoryAdapter adapter = new RosteringRunRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		RosteringRunJpaEntity entity = new RosteringRunJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<RosteringRun> found = adapter.findById(7L);

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
		RosteringRunJpaEntity entity = new RosteringRunJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(RosteringRunJpaEntity.class))).thenReturn(entity);

		RosteringRun saved = adapter.save(RosteringRunMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
