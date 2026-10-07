package sg.nus.carelink.report.controller.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportContent;

/**
 * Explicit family detail fields; internal actor IDs never enter the response.
 *
 * @author Wang Zhili
 */
public record FamilyReportDetailResponse(Long id, Long elderId, Report.Audience audience,
		LocalDate periodStart, LocalDate periodEnd, Report.Status status, boolean dataComplete,
		List<String> missingItems, ReportContent.GeneratedBy generatedBy, OffsetDateTime createdAt,
		OffsetDateTime archivedAt, List<Section> sections, String disclaimer, List<Amendment> amendments) {

	static final String FAMILY_DISCLAIMER = "This summary records care observations and services; it is not a diagnosis or medical advice.";

	public static FamilyReportDetailResponse of(Report report) {
		var metadata = FamilyReportPageResponse.Item.of(report);
		return new FamilyReportDetailResponse(metadata.id(), metadata.elderId(), metadata.audience(),
				metadata.periodStart(), metadata.periodEnd(), metadata.status(), metadata.dataComplete(),
				metadata.missingItems(), metadata.generatedBy(), metadata.createdAt(), metadata.archivedAt(),
				report.content().familySections().stream().map(section -> new Section(section.title(), section.body())).toList(),
				FAMILY_DISCLAIMER,
				report.amendments().stream().map(note -> new Amendment(note.id(), note.note(),
						note.createdAt().atZone(ZoneId.of("Asia/Singapore")).toOffsetDateTime())).toList());
	}

	public record Section(String title, String body) {
	}

	public record Amendment(Long id, String note, OffsetDateTime createdAt) {
	}
}
