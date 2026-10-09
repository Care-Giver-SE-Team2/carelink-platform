package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.infrastructure.persistence.entity.CaregiverJpaEntity;
import sg.nus.carelink.profile.infrastructure.persistence.repository.CaregiverJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class CaregiverRepositoryAdapterTest {

	private final CaregiverJpaRepository jpa = mock(CaregiverJpaRepository.class);
	private final CaregiverRepositoryAdapter adapter = new CaregiverRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		CaregiverJpaEntity entity = new CaregiverJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<Caregiver> found = adapter.findById(7L);

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
		CaregiverJpaEntity entity = new CaregiverJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(CaregiverJpaEntity.class))).thenReturn(entity);

		Caregiver saved = adapter.save(CaregiverMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
