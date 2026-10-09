package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.CaregiverAvailability;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.CaregiverAvailabilityJpaEntity;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.CaregiverAvailabilityJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class CaregiverAvailabilityRepositoryAdapterTest {

	private final CaregiverAvailabilityJpaRepository jpa = mock(CaregiverAvailabilityJpaRepository.class);
	private final CaregiverAvailabilityRepositoryAdapter adapter = new CaregiverAvailabilityRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		CaregiverAvailabilityJpaEntity entity = new CaregiverAvailabilityJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<CaregiverAvailability> found = adapter.findById(7L);

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
		CaregiverAvailabilityJpaEntity entity = new CaregiverAvailabilityJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(CaregiverAvailabilityJpaEntity.class))).thenReturn(entity);

		CaregiverAvailability saved = adapter.save(CaregiverAvailabilityMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
