package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.infrastructure.persistence.entity.FamilyMemberJpaEntity;
import sg.nus.carelink.profile.infrastructure.persistence.repository.FamilyMemberJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class FamilyMemberRepositoryAdapterTest {

	private final FamilyMemberJpaRepository jpa = mock(FamilyMemberJpaRepository.class);
	private final FamilyMemberRepositoryAdapter adapter = new FamilyMemberRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		FamilyMemberJpaEntity entity = new FamilyMemberJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<FamilyMember> found = adapter.findById(7L);

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
		FamilyMemberJpaEntity entity = new FamilyMemberJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(FamilyMemberJpaEntity.class))).thenReturn(entity);

		FamilyMember saved = adapter.save(FamilyMemberMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
