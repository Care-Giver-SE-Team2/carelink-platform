package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.AbsenceReportJpaEntity;
import sg.nus.carelink.rostering.infrastructure.persistence.repository.AbsenceReportJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class AbsenceReportRepositoryAdapterTest {

	private final AbsenceReportJpaRepository jpa = mock(AbsenceReportJpaRepository.class);
	private final AbsenceReportRepositoryAdapter adapter = new AbsenceReportRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		AbsenceReportJpaEntity entity = new AbsenceReportJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<AbsenceReport> found = adapter.findById(7L);

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
		AbsenceReportJpaEntity entity = new AbsenceReportJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(AbsenceReportJpaEntity.class))).thenReturn(entity);

		AbsenceReport saved = adapter.save(AbsenceReportMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
