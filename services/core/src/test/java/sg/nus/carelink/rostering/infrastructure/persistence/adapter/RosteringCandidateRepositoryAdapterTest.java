package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.RosteringCandidate;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringCandidateJpaEntity;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.RosteringCandidateJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class RosteringCandidateRepositoryAdapterTest {

	private final RosteringCandidateJpaRepository jpa = mock(RosteringCandidateJpaRepository.class);
	private final RosteringCandidateRepositoryAdapter adapter = new RosteringCandidateRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		RosteringCandidateJpaEntity entity = new RosteringCandidateJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<RosteringCandidate> found = adapter.findById(7L);

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
		RosteringCandidateJpaEntity entity = new RosteringCandidateJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(RosteringCandidateJpaEntity.class))).thenReturn(entity);

		RosteringCandidate saved = adapter.save(RosteringCandidateMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
