package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.CaregiverPreference;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.CaregiverPreferenceJpaEntity;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.CaregiverPreferenceJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class CaregiverPreferenceRepositoryAdapterTest {

	private final CaregiverPreferenceJpaRepository jpa = mock(CaregiverPreferenceJpaRepository.class);
	private final CaregiverPreferenceRepositoryAdapter adapter = new CaregiverPreferenceRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		CaregiverPreferenceJpaEntity entity = new CaregiverPreferenceJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<CaregiverPreference> found = adapter.findById(7L);

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
		CaregiverPreferenceJpaEntity entity = new CaregiverPreferenceJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(CaregiverPreferenceJpaEntity.class))).thenReturn(entity);

		CaregiverPreference saved = adapter.save(CaregiverPreferenceMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
