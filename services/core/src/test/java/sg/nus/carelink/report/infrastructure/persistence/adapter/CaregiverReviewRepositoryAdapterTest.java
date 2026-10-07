package sg.nus.carelink.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.domain.model.CaregiverReview;
import sg.nus.carelink.report.infrastructure.persistence.entity.CaregiverReviewJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.repository.CaregiverReviewJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class CaregiverReviewRepositoryAdapterTest {

	private final CaregiverReviewJpaRepository jpa = mock(CaregiverReviewJpaRepository.class);
	private final CaregiverReviewRepositoryAdapter adapter = new CaregiverReviewRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		CaregiverReviewJpaEntity entity = new CaregiverReviewJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<CaregiverReview> found = adapter.findById(7L);

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
		CaregiverReviewJpaEntity entity = new CaregiverReviewJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(CaregiverReviewJpaEntity.class))).thenReturn(entity);

		CaregiverReview saved = adapter.save(CaregiverReviewMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
