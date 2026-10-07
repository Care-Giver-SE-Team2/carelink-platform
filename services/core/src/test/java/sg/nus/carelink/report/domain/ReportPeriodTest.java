package sg.nus.carelink.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * Where a period begins and ends, worked out once in Java so that no query has to ask the
 * database what day it is.
 */
class ReportPeriodTest {

	@Test
	void coversFromTheFirstMidnightUpToTheMidnightAfterTheLastDay() {
		ReportPeriod week = new ReportPeriod(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20));

		assertThat(week.startsAt()).isEqualTo(LocalDateTime.of(2026, 9, 14, 0, 0));
		assertThat(week.endsBefore())
				.as("exclusive: a visit at 23:30 on Sunday is in, one at midnight is the next week's")
				.isEqualTo(LocalDateTime.of(2026, 9, 21, 0, 0));
	}

	@Test
	void aSingleDayIsAPeriod() {
		ReportPeriod day = new ReportPeriod(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14));

		assertThat(day.endsBefore()).isEqualTo(LocalDateTime.of(2026, 9, 15, 0, 0));
	}

	@Test
	void aPeriodCannotEndBeforeItStarts() {
		LocalDate start = LocalDate.of(2026, 9, 20);
		LocalDate end = LocalDate.of(2026, 9, 14);

		assertThatThrownBy(() -> new ReportPeriod(start, end))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageContaining("2026-09-14")
				.extracting(error -> ((BusinessRuleViolation) error).code())
				.isEqualTo("REPORT_PERIOD_INVALID");
	}

	@Test
	void aPeriodNeedsBothEnds() {
		LocalDate day = LocalDate.of(2026, 9, 14);

		assertThatThrownBy(() -> new ReportPeriod(null, day)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ReportPeriod(day, null)).isInstanceOf(NullPointerException.class);
	}

	/**
	 * The scheduled run fires late on Sunday and reports on the week that is ending; the same
	 * rule gives the week that has just ended if it runs on the Monday instead.
	 */
	@ParameterizedTest(name = "run on {0} reports on {1} to {2}")
	@CsvSource({
			"2026-09-27, 2026-09-21, 2026-09-27",   // Sunday: the week ending today
			"2026-09-28, 2026-09-21, 2026-09-27",   // Monday: the week that ended yesterday
			"2026-09-24, 2026-09-14, 2026-09-20",   // Thursday: the last full week
			"2026-09-21, 2026-09-14, 2026-09-20"    // Monday again
	})
	void theWeeklyRunReportsOnTheWeekEndingOnOrBeforeTheDayItRuns(
			LocalDate runOn, LocalDate expectedStart, LocalDate expectedEnd) {

		assertThat(ReportPeriod.weekEndingOnOrBefore(runOn))
				.isEqualTo(new ReportPeriod(expectedStart, expectedEnd));
	}
}
