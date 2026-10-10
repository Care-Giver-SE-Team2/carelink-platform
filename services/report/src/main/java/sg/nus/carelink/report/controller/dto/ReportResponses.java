package sg.nus.carelink.report.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportFigure;
import sg.nus.carelink.report.domain.model.ReportMetrics;
import sg.nus.carelink.report.domain.model.ReportPage;
import sg.nus.carelink.report.domain.model.ReportSection;
import sg.nus.carelink.report.domain.model.ReportSeries;

/**
 * The manager's response shapes of UC-MG07, field for field as {@code docs/api/openapi.yaml}
 * publishes them: {@code Report}, {@code ReportDetail}, {@code ReportAmendment} and the list
 * page.
 *
 * <p>Unlike the incident module, the domain record is never returned as it is. Its content is
 * nested ({@code content.dataComplete}, a {@code period} with two ends) where the contract is
 * flat, and the list must not carry the sections at all - a page of twenty reports would
 * otherwise send twenty reports' text to a table that shows one line of each.
 *
 * <p>The family reads its own projection ({@code FamilyReportDetailResponse}); nothing here is
 * sent to a family.
 */
public final class ReportResponses {

	private ReportResponses() {
	}

	/** The numbers of the basis a report was filed from, or null for a report filed before bases were kept. */
	private static Metrics metricsOf(Report report, Map<Long, ReportMetrics> metrics) {
		ReportMetrics found = report.basisId() == null ? null : metrics.get(report.basisId());
		return found == null ? null : Metrics.of(found);
	}

	/**
	 * The contract's {@code Report}: one filed report without its text. What
	 * {@code POST /generate} answers with, and what each row of the list is.
	 *
	 * @param basisId    the basis the report was assembled from; null before bases were kept
	 * @param metrics    that basis's numbers, the same for the three readers' versions of a period
	 * @param archivedAt always null: a report is filed as PUBLISHED and there is no archiving
	 *                   step after that; kept because the contract publishes the field
	 */
	public record ReportView(
			Long id,
			Long elderId,
			Long basisId,
			Report.Audience audience,
			LocalDate periodStart,
			LocalDate periodEnd,
			Report.Status status,
			boolean dataComplete,
			List<String> missingItems,
			ReportContent.GeneratedBy generatedBy,
			Metrics metrics,
			LocalDateTime createdAt,
			LocalDateTime archivedAt) {

		public static ReportView of(Report report) {
			return of(report, Map.of());
		}

		public static ReportView of(Report report, Map<Long, ReportMetrics> metrics) {
			return new ReportView(
					report.id(),
					report.elderId(),
					report.basisId(),
					report.audience(),
					report.period().start(),
					report.period().end(),
					report.status(),
					report.content().dataComplete(),
					report.content().missingItems(),
					report.content().generatedBy(),
					metricsOf(report, metrics),
					report.createdAt(),
					null);
		}
	}

	/** {@code GET /api/reports/{id}}: the contract's {@code ReportDetail}, which is Report plus the text. */
	public record Detail(
			Long id,
			Long elderId,
			Long basisId,
			Report.Audience audience,
			LocalDate periodStart,
			LocalDate periodEnd,
			Report.Status status,
			boolean dataComplete,
			List<String> missingItems,
			ReportContent.GeneratedBy generatedBy,
			Metrics metrics,
			LocalDateTime createdAt,
			LocalDateTime archivedAt,
			List<Section> sections,
			String disclaimer,
			List<Amendment> amendments) {

		public static Detail of(Report report) {
			return of(report, Map.of());
		}

		public static Detail of(Report report, Map<Long, ReportMetrics> metrics) {
			ReportView view = ReportView.of(report, metrics);
			return new Detail(
					view.id(),
					view.elderId(),
					view.basisId(),
					view.audience(),
					view.periodStart(),
					view.periodEnd(),
					view.status(),
					view.dataComplete(),
					view.missingItems(),
					view.generatedBy(),
					view.metrics(),
					view.createdAt(),
					view.archivedAt(),
					report.content().sections().stream().map(Section::of).toList(),
					report.content().disclaimer(),
					report.amendments().stream().map(Amendment::of).toList());
		}
	}

	/**
	 * One titled section: its text, the numbers it states and the series it summarises.
	 *
	 * @param key stable identifier made from the title: "vital-signs"
	 */
	public record Section(String key, String title, String body, List<Figure> figures, List<Series> series) {

		static Section of(ReportSection section) {
			return new Section(
					section.key(),
					section.title(),
					section.body(),
					section.figures().stream().map(Figure::of).toList(),
					section.series().stream().map(Series::of).toList());
		}
	}

	/** One number a section states; {@code outOf} and {@code unit} may be null. */
	public record Figure(String key, String label, BigDecimal value, BigDecimal outOf, String unit) {

		static Figure of(ReportFigure figure) {
			return new Figure(figure.key(), figure.label(), figure.value(), figure.outOf(), figure.unit());
		}
	}

	/** One metric's points, oldest first. */
	public record Series(String key, String label, String unit, List<Point> points) {

		static Series of(ReportSeries series) {
			return new Series(series.key(), series.label(), series.unit(), series.points().stream().map(Point::of).toList());
		}
	}

	/** A reading ({@code low == high}, a date-time) or a day's range (a date). */
	public record Point(String at, BigDecimal low, BigDecimal high, boolean flagged) {

		static Point of(ReportSeries.Point point) {
			return new Point(point.at(), point.low(), point.high(), point.flagged());
		}
	}

	/** The basis's numbers. */
	public record Metrics(
			int visitsPlanned,
			int visitsCompleted,
			BigDecimal fulfilmentRate,
			int vitalsOutOfRange,
			int incidentCount,
			BigDecimal averageElderRating,
			int ratingCount,
			boolean dataComplete) {

		static Metrics of(ReportMetrics metrics) {
			return new Metrics(
					metrics.visitsPlanned(),
					metrics.visitsCompleted(),
					metrics.fulfilmentRate(),
					metrics.vitalsOutOfRange(),
					metrics.incidentCount(),
					metrics.averageElderRating(),
					metrics.ratingCount(),
					metrics.dataComplete());
		}
	}

	/** {@code POST /api/reports/{id}/amendments} and each note on a detail: a correction or a follow-up. */
	public record Amendment(Long id, ReportAmendment.Kind kind, String note, Long authorUserId, LocalDateTime createdAt) {

		public static Amendment of(ReportAmendment amendment) {
			return new Amendment(
					amendment.id(), amendment.kind(), amendment.note(), amendment.authorUserId(), amendment.createdAt());
		}
	}

	/** {@code GET /api/reports}: one page of the list. */
	public record Page(List<ReportView> items, int page, int size, long totalElements) {

		public static Page of(ReportPage page) {
			return of(page, Map.of());
		}

		public static Page of(ReportPage page, Map<Long, ReportMetrics> metrics) {
			return new Page(
					page.items().stream().map(report -> ReportView.of(report, metrics)).toList(),
					page.page(),
					page.size(),
					page.totalElements());
		}
	}
}
