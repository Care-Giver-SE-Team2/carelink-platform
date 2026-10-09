package sg.nus.carelink.careplan.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.careplan.domain.model.CarePlanNode;
import sg.nus.carelink.careplan.infrastructure.persistence.entity.CarePlanNodeJpaEntity;
import sg.nus.carelink.careplan.infrastructure.persistence.repository.CarePlanNodeJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class CarePlanNodeRepositoryAdapterTest {

	private final CarePlanNodeJpaRepository jpa = mock(CarePlanNodeJpaRepository.class);
	private final CarePlanNodeRepositoryAdapter adapter = new CarePlanNodeRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		CarePlanNodeJpaEntity entity = new CarePlanNodeJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<CarePlanNode> found = adapter.findById(7L);

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
		CarePlanNodeJpaEntity entity = new CarePlanNodeJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(CarePlanNodeJpaEntity.class))).thenReturn(entity);

		CarePlanNode saved = adapter.save(CarePlanNodeMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}

	@Test
	void findByCarePlanIdMapsEachEntityToTheDomainModel() {
		CarePlanNodeJpaEntity entity = new CarePlanNodeJpaEntity();
		entity.setId(7L);
		when(jpa.findByCarePlanId(3L)).thenReturn(List.of(entity));

		List<CarePlanNode> found = adapter.findByCarePlanId(3L);

		assertThat(found).extracting(CarePlanNode::id).containsExactly(7L);
	}

	@Test
	void deleteByCarePlanIdDelegatesToSpringData() {
		adapter.deleteByCarePlanId(3L);

		verify(jpa).deleteByCarePlanId(3L);
	}
}
