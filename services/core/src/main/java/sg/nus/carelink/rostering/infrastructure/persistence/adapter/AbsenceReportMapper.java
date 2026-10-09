package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.AbsenceReportJpaEntity;

/**
 * JPA entity <-> domain model for absence_report, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by AbsenceReportMapperTest.
 */
final class AbsenceReportMapper {

	private AbsenceReportMapper() {
	}

	static AbsenceReport toDomain(AbsenceReportJpaEntity e) {
		return new AbsenceReport(
				e.getId(),
				e.getCaregiverId(),
				e.getReviewedByUserId(),
				e.getType() == null ? null : AbsenceReport.Type.valueOf(e.getType().name()),
				e.getStartDate(),
				e.getEndDate(),
				e.getReason(),
				e.getStatus() == null ? null : AbsenceReport.Status.valueOf(e.getStatus().name()),
				e.getCreatedAt(),
				e.getUpdatedAt(),
				e.getCoverageConfirmedAt(),
				e.getCoverageConfirmedByUserId());
	}

	static AbsenceReportJpaEntity toEntity(AbsenceReport d) {
		AbsenceReportJpaEntity e = new AbsenceReportJpaEntity();
		e.setId(d.id());
		e.setCaregiverId(d.caregiverId());
		e.setReviewedByUserId(d.reviewedByUserId());
		e.setType(d.type() == null ? null : AbsenceReportJpaEntity.Type.valueOf(d.type().name()));
		e.setStartDate(d.startDate());
		e.setEndDate(d.endDate());
		e.setReason(d.reason());
		e.setStatus(d.status() == null ? null : AbsenceReportJpaEntity.Status.valueOf(d.status().name()));
		e.setCoverageConfirmedAt(d.coverageConfirmedAt());
		e.setCoverageConfirmedByUserId(d.coverageConfirmedByUserId());
		return e;
	}
}
