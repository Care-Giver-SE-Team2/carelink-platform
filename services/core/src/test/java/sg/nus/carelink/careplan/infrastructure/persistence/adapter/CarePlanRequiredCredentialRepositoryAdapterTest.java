package sg.nus.carelink.careplan.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.careplan.domain.model.CarePlanRequiredCredential;
import sg.nus.carelink.careplan.infrastructure.persistence.entity.CarePlanRequiredCredentialJpaEntity;
import sg.nus.carelink.careplan.infrastructure.persistence.repository.CarePlanRequiredCredentialJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class CarePlanRequiredCredentialRepositoryAdapterTest {

	private final CarePlanRequiredCredentialJpaRepository jpa = mock(CarePlanRequiredCredentialJpaRepository.class);
	private final CarePlanRequiredCredentialRepositoryAdapter adapter = new CarePlanRequiredCredentialRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		CarePlanRequiredCredentialJpaEntity entity = new CarePlanRequiredCredentialJpaEntity();
		entity.setId(new CarePlanRequiredCredentialJpaEntity.Id(1L, 2L));
		when(jpa.findById(new CarePlanRequiredCredentialJpaEntity.Id(1L, 2L))).thenReturn(Optional.of(entity));

		Optional<CarePlanRequiredCredential> found = adapter.findById(new CarePlanRequiredCredential.Id(1L, 2L));

		assertThat(found).isPresent();
	}

	@Test
	void findByIdIsEmptyWhenThereIsNoRow() {
		when(jpa.findById(any())).thenReturn(Optional.empty());

		assertThat(adapter.findById(new CarePlanRequiredCredential.Id(1L, 2L))).isEmpty();
	}

	@Test
	void saveGoesThroughSpringDataAndComesBackAsDomain() {
		CarePlanRequiredCredentialJpaEntity entity = new CarePlanRequiredCredentialJpaEntity();
		entity.setId(new CarePlanRequiredCredentialJpaEntity.Id(1L, 2L));
		when(jpa.save(any(CarePlanRequiredCredentialJpaEntity.class))).thenReturn(entity);

		CarePlanRequiredCredential saved = adapter.save(CarePlanRequiredCredentialMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
