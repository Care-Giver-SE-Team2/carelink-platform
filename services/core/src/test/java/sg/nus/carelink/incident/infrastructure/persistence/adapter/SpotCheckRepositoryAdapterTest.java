package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.SpotCheck;
import sg.nus.carelink.incident.infrastructure.persistence.entity.SpotCheckJpaEntity;
import sg.nus.carelink.incident.infrastructure.persistence.repository.SpotCheckJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class SpotCheckRepositoryAdapterTest {

	private final SpotCheckJpaRepository jpa = mock(SpotCheckJpaRepository.class);
	private final SpotCheckRepositoryAdapter adapter = new SpotCheckRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		SpotCheckJpaEntity entity = new SpotCheckJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<SpotCheck> found = adapter.findById(7L);

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
		SpotCheckJpaEntity entity = new SpotCheckJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(SpotCheckJpaEntity.class))).thenReturn(entity);

		SpotCheck saved = adapter.save(SpotCheckMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}

	@Test
	void findersPickTheirQueryAndAnEmptyElderListAsksNothing() {
		SpotCheckJpaEntity row = new SpotCheckJpaEntity();
		row.setId(7L);
		row.setApprovalStatus(SpotCheckJpaEntity.ApprovalStatus.APPROVED);
		LocalDateTime since = LocalDateTime.of(2026, 7, 1, 0, 0);
		when(jpa.findAllByOrderByProposedTimeDescIdDesc()).thenReturn(List.of(row));
		when(jpa.findByElderIdInOrderByProposedTimeDescIdDesc(Set.of(2L))).thenReturn(List.of(row));
		when(jpa.findByCaregiverIdOrderByProposedTimeDescIdDesc(3L)).thenReturn(List.of(row, row));
		when(jpa.findByOutcomeAndCheckedAtGreaterThanEqual(SpotCheckJpaEntity.Outcome.COMPLETED, since))
				.thenReturn(List.of(row));

		assertThat(adapter.findAll()).hasSize(1);
		assertThat(adapter.findByElderIds(Set.of(2L))).hasSize(1);
		assertThat(adapter.findByElderIds(Set.of())).isEmpty();
		assertThat(adapter.findByCaregiverId(3L)).hasSize(2);
		assertThat(adapter.findConcludedSince(since)).extracting(SpotCheck::id).containsExactly(7L);
	}
}
