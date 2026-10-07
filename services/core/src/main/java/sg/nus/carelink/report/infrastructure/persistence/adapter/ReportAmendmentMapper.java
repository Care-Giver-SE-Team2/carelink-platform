package sg.nus.carelink.report.infrastructure.persistence.adapter;

import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.infrastructure.persistence.entity.ReportAmendmentJpaEntity;

/**
 * JPA entity <-> domain model for report_amendment, both directions, column by column.
 * Covered by ReportMapperTest.
 */
final class ReportAmendmentMapper {

	private ReportAmendmentMapper() {
	}

	static ReportAmendment toDomain(ReportAmendmentJpaEntity e) {
		return new ReportAmendment(e.getId(), e.getReportId(), e.getNote(), e.getAuthorUserId(), e.getCreatedAt());
	}

	static ReportAmendmentJpaEntity toEntity(ReportAmendment d) {
		ReportAmendmentJpaEntity e = new ReportAmendmentJpaEntity();
		e.setId(d.id());
		e.setReportId(d.reportId());
		e.setNote(d.note());
		e.setAuthorUserId(d.authorUserId());
		e.setCreatedAt(d.createdAt());
		return e;
	}
}
