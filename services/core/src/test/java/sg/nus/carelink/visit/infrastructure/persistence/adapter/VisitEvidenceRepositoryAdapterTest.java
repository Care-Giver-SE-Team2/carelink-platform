package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.domain.model.VisitEvidence;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitEvidenceJpaEntity;
import sg.nus.carelink.visit.infrastructure.persistence.repository.VisitEvidenceJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class VisitEvidenceRepositoryAdapterTest {

	private final VisitEvidenceJpaRepository jpa = mock(VisitEvidenceJpaRepository.class);
	private final VisitEvidenceRepositoryAdapter adapter = new VisitEvidenceRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		VisitEvidenceJpaEntity entity = new VisitEvidenceJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<VisitEvidence> found = adapter.findById(7L);

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
		VisitEvidenceJpaEntity entity = new VisitEvidenceJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(VisitEvidenceJpaEntity.class))).thenReturn(entity);

		VisitEvidence saved = adapter.save(VisitEvidenceMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
