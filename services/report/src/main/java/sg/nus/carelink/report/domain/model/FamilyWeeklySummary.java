package sg.nus.carelink.report.domain.model;

import java.util.stream.Collectors;

/**
 * Fixed-format text from an authorized report's family chapters, without generating new content.
 *
 * @author Wang Zhili
 */
public record FamilyWeeklySummary(Long reportId, Long elderId, ReportPeriod period, String summaryText) {

	public static FamilyWeeklySummary of(Report report) {
		String text = report.content().familySections().stream()
				.map(section -> section.title() + "\n" + section.body())
				.collect(Collectors.joining("\n\n"));
		if (text.isEmpty()) {
			throw new IllegalStateException("The stored report has no family chapters to summarize");
		}
		return new FamilyWeeklySummary(report.id(), report.elderId(), report.period(), text);
	}
}
