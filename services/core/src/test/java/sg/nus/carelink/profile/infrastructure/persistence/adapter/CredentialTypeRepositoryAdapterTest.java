package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.CredentialType;
import sg.nus.carelink.profile.infrastructure.persistence.entity.CredentialTypeJpaEntity;
import sg.nus.carelink.profile.infrastructure.persistence.repository.CredentialTypeJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class CredentialTypeRepositoryAdapterTest {

	private final CredentialTypeJpaRepository jpa = mock(CredentialTypeJpaRepository.class);
	private final CredentialTypeRepositoryAdapter adapter = new CredentialTypeRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		CredentialTypeJpaEntity entity = new CredentialTypeJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<CredentialType> found = adapter.findById(7L);

		assertThat(found).isPresent();
		assertThat(found.get().id()).isEqualTo(7L);
	}

	@Test
	void findByIdIsEmptyWhenThereIsNoRow() {
		when(jpa.findById(any())).thenReturn(Optional.empty());

		assertThat(adapter.findById(7L)).isEmpty();
	}

	@Test
	void batchQueryLoadsOnlyRequestedTypeIdentifiersAndMapsTheirNames() {
		var ids = Set.of(11L, 12L);
		var first = new CredentialTypeJpaEntity();
		first.setId(12L);
		first.setName("Care Orientation");
		var second = new CredentialTypeJpaEntity();
		second.setId(11L);
		second.setName("First Aid");
		when(jpa.findAllById(ids)).thenReturn(List.of(first, second));

		assertThat(adapter.findByIds(ids)).containsExactly(
				new CredentialType(12L, "Care Orientation", null),
				new CredentialType(11L, "First Aid", null));
		verify(jpa).findAllById(ids);
		verifyNoMoreInteractions(jpa);
	}

	@Test
	void emptyTypeSetDoesNotQueryStorage() {
		assertThat(adapter.findByIds(Set.of())).isEmpty();
		verifyNoInteractions(jpa);
	}

	@Test
	void missingTypeRowsRemainAbsentForTheCallingServiceToHandle() {
		var ids = Set.of(11L);
		when(jpa.findAllById(ids)).thenReturn(List.of());

		assertThat(adapter.findByIds(ids)).isEmpty();
		verify(jpa).findAllById(ids);
		verifyNoMoreInteractions(jpa);
	}

	@Test
	void saveGoesThroughSpringDataAndComesBackAsDomain() {
		CredentialTypeJpaEntity entity = new CredentialTypeJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(CredentialTypeJpaEntity.class))).thenReturn(entity);

		CredentialType saved = adapter.save(CredentialTypeMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
