package sg.nus.carelink.report.controller.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.domain.model.ReportFigure;
import sg.nus.carelink.report.domain.model.ReportSection;
import sg.nus.carelink.report.domain.model.ReportSeries;

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
				report.content().familySections().stream().map(Section::of).toList(),
				FAMILY_DISCLAIMER,
				report.amendments().stream().map(note -> new Amendment(note.id(), note.kind(), note.note(),
						note.createdAt().atZone(ZoneId.of("Asia/Singapore")).toOffsetDateTime())).toList());
	}

	/** Only the authorized FAMILY report's saved values enter this projection, never its internal basis. */
	public record Section(String key, String title, String body, List<ReportFigure> figures, List<ReportSeries> series) {
		static Section of(ReportSection section) {
			return new Section(section.key(), section.title(), section.body(), section.figures(), section.series());
		}
	}

	public record Amendment(Long id, ReportAmendment.Kind kind, String note, OffsetDateTime createdAt) {
	}
}
