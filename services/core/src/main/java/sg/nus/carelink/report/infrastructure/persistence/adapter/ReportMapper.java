package sg.nus.carelink.report.infrastructure.persistence.adapter;

import java.util.List;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.infrastructure.persistence.entity.ReportAmendmentJpaEntity;
import sg.nus.carelink.report.infrastructure.persistence.entity.ReportJpaEntity;

/**
 * JPA entity <-> domain model for report, both directions, column by column. Covered by
 * ReportMapperTest.
 *
 * <p>The content column crosses as JSON through {@link ReportContentJson}; the corrections
 * live in their own table and are handed in by the adapter, which reads them alongside.
 * created_at is written from the domain's timestamp: see ReportJpaEntity for why the
 * column's default is not used.
 */
final class ReportMapper {

	private ReportMapper() {
	}

	static Report toDomain(ReportJpaEntity e, List<ReportAmendmentJpaEntity> amendments) {
		return new Report(
				e.getId(),
				e.getElderId(),
				e.getGeneratedByUserId(),
				Report.Audience.valueOf(e.getAudience().name()),
				new ReportPeriod(e.getPeriodStart(), e.getPeriodEnd()),
				Report.Status.valueOf(e.getStatus().name()),
				ReportContentJson.read(e.getContent()),
				amendments.stream().map(ReportAmendmentMapper::toDomain).toList(),
				e.getCreatedAt());
	}

	static ReportJpaEntity toEntity(Report d) {
		ReportJpaEntity e = new ReportJpaEntity();
		e.setId(d.id());
		e.setElderId(d.elderId());
		e.setGeneratedByUserId(d.generatedByUserId());
		e.setAudience(ReportJpaEntity.Audience.valueOf(d.audience().name()));
		e.setPeriodStart(d.period().start());
		e.setPeriodEnd(d.period().end());
		e.setStatus(ReportJpaEntity.Status.valueOf(d.status().name()));
		e.setContent(ReportContentJson.write(d.content()));
		e.setCreatedAt(d.createdAt());
		return e;
	}
}
