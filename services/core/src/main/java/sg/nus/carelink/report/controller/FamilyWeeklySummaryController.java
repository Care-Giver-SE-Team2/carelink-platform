package sg.nus.carelink.report.controller;

import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import sg.nus.carelink.report.application.FamilyReportQueryService;
import sg.nus.carelink.report.controller.dto.FamilyWeeklySummaryResponse;
import sg.nus.carelink.report.domain.model.ReportPeriod;

/**
 * Reads a filed family report for an explicitly selected Singapore calendar week.
 *
 * @author Wang Zhili
 */
@RestController
public class FamilyWeeklySummaryController {

	private static final LocalDate MIN_DATE = LocalDate.of(1000, 1, 1);
	private static final LocalDate MAX_WEEK_START = LocalDate.of(9999, 12, 31).minusDays(6);

	private final FamilyReportQueryService reports;

	public FamilyWeeklySummaryController(FamilyReportQueryService reports) {
		this.reports = reports;
	}

	@GetMapping("/api/elders/{elderId}/weekly-summary")
	@PreAuthorize("hasRole('FAMILY')")
	public FamilyWeeklySummaryResponse get(@PathVariable Long elderId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart, Principal principal) {
		if (weekStart.isBefore(MIN_DATE) || weekStart.isAfter(MAX_WEEK_START)
				|| !DayOfWeek.MONDAY.equals(weekStart.getDayOfWeek())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"weekStart must be a Monday whose full week is within 1000-01-01 and 9999-12-31");
		}
		return FamilyWeeklySummaryResponse.of(reports.findWeeklySummary(principal.getName(), elderId,
				new ReportPeriod(weekStart, weekStart.plusDays(6))));
	}
}
