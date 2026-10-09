package sg.nus.carelink.careplan.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.infrastructure.persistence.entity.CarePlanJpaEntity;
import sg.nus.carelink.careplan.infrastructure.persistence.repository.CarePlanJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class CarePlanRepositoryAdapterTest {

	private final CarePlanJpaRepository jpa = mock(CarePlanJpaRepository.class);
	private final CarePlanRepositoryAdapter adapter = new CarePlanRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		CarePlanJpaEntity entity = new CarePlanJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<CarePlan> found = adapter.findById(7L);

		assertThat(found).isPresent();
		assertThat(found.get().id()).isEqualTo(7L);
	}

	@Test
	void findByIdIsEmptyWhenThereIsNoRow() {
		when(jpa.findById(any())).thenReturn(Optional.empty());

		assertThat(adapter.findById(7L)).isEmpty();
	}

	@Test
	void findLatestByElderIdMapsTheEntityToTheDomainModel() {
		CarePlanJpaEntity entity = new CarePlanJpaEntity();
		entity.setId(7L);
		entity.setElderId(42L);
		when(jpa.findFirstByElderIdOrderByVersionDesc(42L)).thenReturn(Optional.of(entity));

		Optional<CarePlan> found = adapter.findLatestByElderId(42L);

		assertThat(found).isPresent();
		assertThat(found.get().elderId()).isEqualTo(42L);
	}

	@Test
	void saveGoesThroughSpringDataAndComesBackAsDomain() {
		CarePlanJpaEntity entity = new CarePlanJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(CarePlanJpaEntity.class))).thenReturn(entity);

		CarePlan saved = adapter.save(CarePlanMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
