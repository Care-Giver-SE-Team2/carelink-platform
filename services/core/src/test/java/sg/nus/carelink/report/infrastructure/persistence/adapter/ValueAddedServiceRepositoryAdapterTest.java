package sg.nus.carelink.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.domain.model.ValueAddedService;
import sg.nus.carelink.report.infrastructure.persistence.entity.ValueAddedServiceJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.repository.ValueAddedServiceJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class ValueAddedServiceRepositoryAdapterTest {

	private final ValueAddedServiceJpaRepository jpa = mock(ValueAddedServiceJpaRepository.class);
	private final ValueAddedServiceRepositoryAdapter adapter = new ValueAddedServiceRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		ValueAddedServiceJpaEntity entity = new ValueAddedServiceJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<ValueAddedService> found = adapter.findById(7L);

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
		ValueAddedServiceJpaEntity entity = new ValueAddedServiceJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(ValueAddedServiceJpaEntity.class))).thenReturn(entity);

		ValueAddedService saved = adapter.save(ValueAddedServiceMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
