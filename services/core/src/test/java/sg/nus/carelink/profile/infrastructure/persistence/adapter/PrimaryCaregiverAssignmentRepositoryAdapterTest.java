package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.PrimaryCaregiverAssignment;
import sg.nus.carelink.profile.infrastructure.persistence.entity.PrimaryCaregiverAssignmentJpaEntity;
import sg.nus.carelink.profile.infrastructure.persistence.repository.PrimaryCaregiverAssignmentJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class PrimaryCaregiverAssignmentRepositoryAdapterTest {

	private final PrimaryCaregiverAssignmentJpaRepository jpa = mock(PrimaryCaregiverAssignmentJpaRepository.class);
	private final PrimaryCaregiverAssignmentRepositoryAdapter adapter =
			new PrimaryCaregiverAssignmentRepositoryAdapter(jpa);

	@Test
	void findByElderIdMapsTheEntity() {
		when(jpa.findById(1L)).thenReturn(Optional.of(entity(1L, 2L)));

		assertThat(adapter.findByElderId(1L)).get().extracting(PrimaryCaregiverAssignment::caregiverId).isEqualTo(2L);
	}

	@Test
	void findByElderIdsMapsEveryRow() {
		when(jpa.findByElderIdIn(Set.of(1L, 3L))).thenReturn(List.of(entity(1L, 2L), entity(3L, 4L)));

		assertThat(adapter.findByElderIds(Set.of(1L, 3L))).extracting(PrimaryCaregiverAssignment::elderId)
				.containsExactly(1L, 3L);
	}

	@Test
	void saveGoesThroughSpringData() {
		when(jpa.save(any(PrimaryCaregiverAssignmentJpaEntity.class))).thenReturn(entity(1L, 2L));

		assertThat(adapter.save(new PrimaryCaregiverAssignment(1L, 2L, null)).elderId()).isEqualTo(1L);
	}

	@Test
	void deleteByElderIdDeletesByPrimaryKey() {
		adapter.deleteByElderId(1L);

		verify(jpa).deleteById(1L);
	}

	private static PrimaryCaregiverAssignmentJpaEntity entity(Long elderId, Long caregiverId) {
		PrimaryCaregiverAssignmentJpaEntity entity = new PrimaryCaregiverAssignmentJpaEntity();
		entity.setElderId(elderId);
		entity.setCaregiverId(caregiverId);
		return entity;
	}
}
