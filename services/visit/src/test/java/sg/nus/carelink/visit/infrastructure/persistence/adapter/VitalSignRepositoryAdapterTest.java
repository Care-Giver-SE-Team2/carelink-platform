package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.domain.model.VitalSign;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VitalSignJpaEntity;
import sg.nus.carelink.visit.infrastructure.persistence.repository.VitalSignJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class VitalSignRepositoryAdapterTest {

	private final VitalSignJpaRepository jpa = mock(VitalSignJpaRepository.class);
	private final VitalSignRepositoryAdapter adapter = new VitalSignRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		VitalSignJpaEntity entity = new VitalSignJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<VitalSign> found = adapter.findById(7L);

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
		VitalSignJpaEntity entity = new VitalSignJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(VitalSignJpaEntity.class))).thenReturn(entity);

		VitalSign saved = adapter.save(VitalSignMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
