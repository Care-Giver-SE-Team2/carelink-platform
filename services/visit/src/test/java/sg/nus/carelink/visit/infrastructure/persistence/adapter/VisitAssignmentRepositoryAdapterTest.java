package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.domain.model.VisitAssignment;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitAssignmentJpaEntity;
import sg.nus.carelink.visit.infrastructure.persistence.repository.VisitAssignmentJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class VisitAssignmentRepositoryAdapterTest {

	private final VisitAssignmentJpaRepository jpa = mock(VisitAssignmentJpaRepository.class);
	private final VisitAssignmentRepositoryAdapter adapter = new VisitAssignmentRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		VisitAssignmentJpaEntity entity = new VisitAssignmentJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<VisitAssignment> found = adapter.findById(7L);

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
		VisitAssignmentJpaEntity entity = new VisitAssignmentJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(VisitAssignmentJpaEntity.class))).thenReturn(entity);

		VisitAssignment saved = adapter.save(VisitAssignmentMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}

	@Test
	void findsTheCurrentAssignmentAndTheWholeHistory() {
		VisitAssignmentJpaEntity active = new VisitAssignmentJpaEntity();
		active.setId(8L);
		active.setStatus(VisitAssignmentJpaEntity.Status.ACTIVE);
		VisitAssignmentJpaEntity replaced = new VisitAssignmentJpaEntity();
		replaced.setId(7L);
		replaced.setStatus(VisitAssignmentJpaEntity.Status.REPLACED);
		when(jpa.findFirstByVisitIdAndStatusOrderByIdDesc(30L, VisitAssignmentJpaEntity.Status.ACTIVE))
				.thenReturn(Optional.of(active));
		when(jpa.findByVisitIdOrderByIdAsc(30L)).thenReturn(java.util.List.of(replaced, active));

		assertThat(adapter.findActiveByVisitId(30L)).get().extracting(VisitAssignment::id).isEqualTo(8L);
		assertThat(adapter.findByVisitId(30L)).extracting(VisitAssignment::id).containsExactly(7L, 8L);
	}
}
