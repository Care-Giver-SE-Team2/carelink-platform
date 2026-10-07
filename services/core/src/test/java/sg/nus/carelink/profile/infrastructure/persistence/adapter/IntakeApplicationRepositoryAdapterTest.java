package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.infrastructure.persistence.entity.IntakeApplicationJpaEntity;
import sg.nus.carelink.profile.infrastructure.persistence.repository.IntakeApplicationJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class IntakeApplicationRepositoryAdapterTest {

	private final IntakeApplicationJpaRepository jpa = mock(IntakeApplicationJpaRepository.class);
	private final IntakeApplicationRepositoryAdapter adapter = new IntakeApplicationRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		IntakeApplicationJpaEntity entity = new IntakeApplicationJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<IntakeApplication> found = adapter.findById(7L);

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
		IntakeApplicationJpaEntity entity = new IntakeApplicationJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(IntakeApplicationJpaEntity.class))).thenReturn(entity);

		IntakeApplication saved = adapter.save(IntakeApplicationMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
