package sg.nus.carelink.report.infrastructure.persistence.adapter;

import sg.nus.carelink.report.domain.model.ReportBasis;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportMetrics;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.infrastructure.persistence.entity.ReportBasisJpaEntity;

/**
 * JPA entity <-> domain model for report_basis, column by column. Covered by ReportBasisMapperTest.
 *
 * <p>The facts go one way only: they are written beside the basis as a document and never
 * read back into the domain, which works from the numbers.
 */
final class ReportBasisMapper {

	private ReportBasisMapper() {
	}

	static ReportBasis toDomain(ReportBasisJpaEntity e) {
		return new ReportBasis(
				e.getId(),
				e.getElderId(),
				new ReportPeriod(e.getPeriodStart(), e.getPeriodEnd()),
				metrics(e),
				e.getCreatedAt());
	}

	static ReportMetrics metrics(ReportBasisJpaEntity e) {
		return new ReportMetrics(
				e.getVisitsPlanned(),
				e.getVisitsCompleted(),
				e.getFulfilmentRate(),
				e.getVitalsOutOfRange(),
				e.getIncidentCount(),
				e.getAvgElderRating(),
				e.getRatingCount(),
				e.isDataComplete());
	}

	static ReportBasisJpaEntity toEntity(ReportBasis d, ReportFacts facts) {
		ReportBasisJpaEntity e = new ReportBasisJpaEntity();
		e.setId(d.id());
		e.setElderId(d.elderId());
		e.setPeriodStart(d.period().start());
		e.setPeriodEnd(d.period().end());
		e.setFacts(ReportBasisJson.write(facts));
		e.setFactsVersion(ReportBasisJson.VERSION);
		ReportMetrics metrics = d.metrics();
		e.setVisitsPlanned(metrics.visitsPlanned());
		e.setVisitsCompleted(metrics.visitsCompleted());
		e.setFulfilmentRate(metrics.fulfilmentRate());
		e.setVitalsOutOfRange(metrics.vitalsOutOfRange());
		e.setIncidentCount(metrics.incidentCount());
		e.setAvgElderRating(metrics.averageElderRating());
		e.setRatingCount(metrics.ratingCount());
		e.setDataComplete(metrics.dataComplete());
		e.setCreatedAt(d.createdAt());
		return e;
	}
}
