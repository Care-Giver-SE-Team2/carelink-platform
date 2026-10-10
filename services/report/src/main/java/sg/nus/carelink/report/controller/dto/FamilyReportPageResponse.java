package sg.nus.carelink.report.controller.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportPage;

/**
 * Family list projection: metadata from authorized FAMILY content, without text or actor IDs.
 *
 * @author Wang Zhili
 */
public record FamilyReportPageResponse(List<Item> items, int page, int size, long totalElements) {

	public static FamilyReportPageResponse of(ReportPage page) {
		return new FamilyReportPageResponse(page.items().stream().map(Item::of).toList(),
				page.page(), page.size(), page.totalElements());
	}

	/** No archival timestamp is stored, so archivedAt remains null even for ARCHIVED reports. */
	public record Item(Long id, Long elderId, Report.Audience audience, LocalDate periodStart, LocalDate periodEnd,
			Report.Status status, boolean dataComplete, List<String> missingItems, ReportContent.GeneratedBy generatedBy,
			OffsetDateTime createdAt, OffsetDateTime archivedAt) {

		private static final Pattern VISIT_GAP = Pattern.compile("Visit [0-9]+ on [0-9]{4}-[0-9]{2}-[0-9]{2} not closed");

		static Item of(Report report) {
			// MG07 emits controlled visit gaps. Unfamiliar legacy text is not safe to publish verbatim.
			List<String> gaps = report.content().missingItems().stream()
					.map(item -> VISIT_GAP.matcher(item).matches() ? item : "Some care records are incomplete.")
					.toList();
			return new Item(report.id(), report.elderId(), report.audience(), report.period().start(), report.period().end(),
					report.status(), report.content().dataComplete(), gaps, report.content().generatedBy(),
					report.createdAt().atZone(ZoneId.of("Asia/Singapore")).toOffsetDateTime(), null);
		}
	}
}
