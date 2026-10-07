package sg.nus.carelink.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;
import sg.nus.carelink.report.infrastructure.persistence.entity.ValueAddedServiceRequestJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.repository.ValueAddedServiceRequestJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class ValueAddedServiceRequestRepositoryAdapterTest {

	private final ValueAddedServiceRequestJpaRepository jpa = mock(ValueAddedServiceRequestJpaRepository.class);
	private final ValueAddedServiceRequestRepositoryAdapter adapter = new ValueAddedServiceRequestRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		ValueAddedServiceRequestJpaEntity entity = new ValueAddedServiceRequestJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<ValueAddedServiceRequest> found = adapter.findById(7L);

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
		ValueAddedServiceRequestJpaEntity entity = new ValueAddedServiceRequestJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(ValueAddedServiceRequestJpaEntity.class))).thenReturn(entity);

		ValueAddedServiceRequest saved = adapter.save(ValueAddedServiceRequestMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
