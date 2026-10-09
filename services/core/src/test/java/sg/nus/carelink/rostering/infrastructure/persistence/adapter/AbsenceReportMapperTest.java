package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.AbsenceReportJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class AbsenceReportMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		AbsenceReportJpaEntity entity = new AbsenceReportJpaEntity();
		entity.setId(1L);
		entity.setCaregiverId(2L);
		entity.setReviewedByUserId(3L);
		entity.setType(AbsenceReportJpaEntity.Type.SICK);
		entity.setStartDate(LocalDate.of(2026, 9, 6));
		entity.setEndDate(LocalDate.of(2026, 9, 7));
		entity.setReason("v7");
		entity.setStatus(AbsenceReportJpaEntity.Status.PENDING);
		entity.setCoverageConfirmedAt(LocalDate.of(2026, 9, 7).atTime(15, 0));
		entity.setCoverageConfirmedByUserId(9L);

		AbsenceReport domain = AbsenceReportMapper.toDomain(entity);
		assertThat(domain.coverageConfirmedAt()).isEqualTo(entity.getCoverageConfirmedAt());
		assertThat(domain.coverageConfirmedByUserId()).isEqualTo(entity.getCoverageConfirmedByUserId());
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.caregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(domain.reviewedByUserId()).isEqualTo(entity.getReviewedByUserId());
		assertThat(domain.type().name()).isEqualTo(entity.getType().name());
		assertThat(domain.startDate()).isEqualTo(entity.getStartDate());
		assertThat(domain.endDate()).isEqualTo(entity.getEndDate());
		assertThat(domain.reason()).isEqualTo(entity.getReason());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());

		AbsenceReportJpaEntity back = AbsenceReportMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getCaregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(back.getReviewedByUserId()).isEqualTo(entity.getReviewedByUserId());
		assertThat(back.getType()).isEqualTo(entity.getType());
		assertThat(back.getStartDate()).isEqualTo(entity.getStartDate());
		assertThat(back.getEndDate()).isEqualTo(entity.getEndDate());
		assertThat(back.getReason()).isEqualTo(entity.getReason());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
		assertThat(back.getCoverageConfirmedAt()).isEqualTo(entity.getCoverageConfirmedAt());
		assertThat(back.getCoverageConfirmedByUserId()).isEqualTo(entity.getCoverageConfirmedByUserId());
	}
}
