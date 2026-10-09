package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.Credential;
import sg.nus.carelink.profile.infrastructure.persistence.entity.CredentialJpaEntity;
import sg.nus.carelink.profile.infrastructure.persistence.repository.CredentialJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class CredentialRepositoryAdapterTest {

	private final CredentialJpaRepository jpa = mock(CredentialJpaRepository.class);
	private final CredentialRepositoryAdapter adapter = new CredentialRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		CredentialJpaEntity entity = new CredentialJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<Credential> found = adapter.findById(7L);

		assertThat(found).isPresent();
		assertThat(found.get().id()).isEqualTo(7L);
	}

	@Test
	void findByIdIsEmptyWhenThereIsNoRow() {
		when(jpa.findById(any())).thenReturn(Optional.empty());

		assertThat(adapter.findById(7L)).isEmpty();
	}

	@Test
	void caregiverQueryUsesTheScopedOrderedFinderAndMapsEveryResult() {
		var first = new CredentialJpaEntity();
		first.setId(501L);
		first.setCaregiverId(201L);
		first.setCredentialTypeId(11L);
		first.setIssuingBody("Training Centre");
		first.setExpiryDate(LocalDate.of(2027, 1, 1));
		first.setStatus(CredentialJpaEntity.Status.PUBLISHED);
		var second = new CredentialJpaEntity();
		second.setId(502L);
		second.setCaregiverId(201L);
		second.setCredentialTypeId(11L);
		second.setExpiryDate(LocalDate.of(9999, 12, 31));
		second.setStatus(CredentialJpaEntity.Status.REVOKED);
		when(jpa.findByCaregiverIdOrderByCredentialTypeIdAscIdAsc(201L)).thenReturn(List.of(first, second));

		assertThat(adapter.findByCaregiverId(201L)).containsExactly(
				new Credential(501L, 201L, 11L, null, null, "Training Centre", null,
						LocalDate.of(2027, 1, 1), Credential.Status.PUBLISHED, null, null, null),
				new Credential(502L, 201L, 11L, null, null, null, null,
						LocalDate.of(9999, 12, 31), Credential.Status.REVOKED, null, null, null));
		verify(jpa).findByCaregiverIdOrderByCredentialTypeIdAscIdAsc(201L);
		verifyNoMoreInteractions(jpa);
	}

	@Test
	void caregiverWithNoCredentialRowsReturnsAnEmptyList() {
		when(jpa.findByCaregiverIdOrderByCredentialTypeIdAscIdAsc(201L)).thenReturn(List.of());

		assertThat(adapter.findByCaregiverId(201L)).isEmpty();
		verify(jpa).findByCaregiverIdOrderByCredentialTypeIdAscIdAsc(201L);
		verifyNoMoreInteractions(jpa);
	}

	@Test
	void saveGoesThroughSpringDataAndComesBackAsDomain() {
		CredentialJpaEntity entity = new CredentialJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(CredentialJpaEntity.class))).thenReturn(entity);

		Credential saved = adapter.save(CredentialMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
