package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.RosteringCandidateCheck;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringCandidateCheckJpaEntity;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.RosteringCandidateCheckJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class RosteringCandidateCheckRepositoryAdapterTest {

	private final RosteringCandidateCheckJpaRepository jpa = mock(RosteringCandidateCheckJpaRepository.class);
	private final RosteringCandidateCheckRepositoryAdapter adapter = new RosteringCandidateCheckRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		RosteringCandidateCheckJpaEntity entity = new RosteringCandidateCheckJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<RosteringCandidateCheck> found = adapter.findById(7L);

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
		RosteringCandidateCheckJpaEntity entity = new RosteringCandidateCheckJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(RosteringCandidateCheckJpaEntity.class))).thenReturn(entity);

		RosteringCandidateCheck saved = adapter.save(RosteringCandidateCheckMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
