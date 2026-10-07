package sg.nus.carelink.report.controller.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportPage;

/**
 * The response shapes of UC-MG07, field for field as {@code docs/api/openapi-draft.yaml}
 * publishes them: {@code Report}, {@code ReportDetail}, {@code ReportAmendment} and the list
 * page.
 *
 * <p>Unlike the incident module, the domain record is never returned as it is. Its content is
 * nested ({@code content.dataComplete}, a {@code period} with two ends) where the contract is
 * flat, and the list must not carry the sections at all - a page of twenty reports would
 * otherwise send twenty reports' text to a table that shows one line of each.
 */
public final class ReportResponses {

	private ReportResponses() {
	}

	/**
	 * The contract's {@code Report}: one filed report without its text. What
	 * {@code POST /generate} answers with, and what each row of the list is.
	 *
	 * @param archivedAt always null: a report is filed as PUBLISHED and there is no archiving
	 *                   step after that; kept because the contract publishes the field
	 */
	public record ReportView(
			Long id,
			Long elderId,
			Report.Audience audience,
			LocalDate periodStart,
			LocalDate periodEnd,
			Report.Status status,
			boolean dataComplete,
			List<String> missingItems,
			ReportContent.GeneratedBy generatedBy,
			LocalDateTime createdAt,
			LocalDateTime archivedAt) {

		public static ReportView of(Report report) {
			return new ReportView(
					report.id(),
					report.elderId(),
					report.audience(),
					report.period().start(),
					report.period().end(),
					report.status(),
					report.content().dataComplete(),
					report.content().missingItems(),
					report.content().generatedBy(),
					report.createdAt(),
					null);
		}
	}

	/** {@code GET /api/reports/{id}}: the contract's {@code ReportDetail}, which is Report plus the text. */
	public record Detail(
			Long id,
			Long elderId,
			Report.Audience audience,
			LocalDate periodStart,
			LocalDate periodEnd,
			Report.Status status,
			boolean dataComplete,
			List<String> missingItems,
			ReportContent.GeneratedBy generatedBy,
			LocalDateTime createdAt,
			LocalDateTime archivedAt,
			List<Section> sections,
			String disclaimer,
			List<Amendment> amendments) {

		public static Detail of(Report report) {
			ReportView view = ReportView.of(report);
			return new Detail(
					view.id(),
					view.elderId(),
					view.audience(),
					view.periodStart(),
					view.periodEnd(),
					view.status(),
					view.dataComplete(),
					view.missingItems(),
					view.generatedBy(),
					view.createdAt(),
					view.archivedAt(),
					report.content().sections().stream().map(section -> new Section(section.title(), section.body())).toList(),
					report.content().disclaimer(),
					report.amendments().stream().map(Amendment::of).toList());
		}
	}

	/** One titled section. */
	public record Section(String title, String body) {
	}

	/** {@code POST /api/reports/{id}/amendments} and each correction on a detail. */
	public record Amendment(Long id, String note, Long authorUserId, LocalDateTime createdAt) {

		public static Amendment of(ReportAmendment amendment) {
			return new Amendment(amendment.id(), amendment.note(), amendment.authorUserId(), amendment.createdAt());
		}
	}

	/** {@code GET /api/reports}: one page of the list. */
	public record Page(List<ReportView> items, int page, int size, long totalElements) {

		public static Page of(ReportPage page) {
			return new Page(
					page.items().stream().map(ReportView::of).toList(),
					page.page(),
					page.size(),
					page.totalElements());
		}
	}
}
