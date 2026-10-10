package sg.nus.carelink.report.controller.dto;

import java.time.LocalDate;

import sg.nus.carelink.report.domain.model.FamilyWeeklySummary;
import sg.nus.carelink.report.domain.model.ReportContent;

/**
 * The template summary and its source report, whose detail carries completeness and corrections.
 *
 * @author Wang Zhili
 */
public record FamilyWeeklySummaryResponse(Long reportId, Long elderId, LocalDate periodStart,
		LocalDate periodEnd, String summaryText, ReportContent.GeneratedBy generatedBy, String disclaimer) {

	public static FamilyWeeklySummaryResponse of(FamilyWeeklySummary summary) {
		return new FamilyWeeklySummaryResponse(summary.reportId(), summary.elderId(), summary.period().start(),
				summary.period().end(), summary.summaryText(), ReportContent.GeneratedBy.TEMPLATE,
				FamilyReportDetailResponse.FAMILY_DISCLAIMER);
	}
}
