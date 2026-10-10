package sg.nus.carelink.report.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * The days a periodic report covers, the first and the last both included.
 *
 * <p>Kept as two dates because that is what a report is about - "the week of 14 to 20
 * September" - and what the report table stores. The two moments that bound the records it
 * reads are worked out here and nowhere else: from midnight at the start of the first day up
 * to, but not including, midnight after the last. Every query that selects a period's visits
 * compares against these two values, so a visit at 23:30 on the last day is in and one at
 * 00:00 the next morning is not, whichever query asks.
 *
 * <p>They are computed in Java on purpose rather than with {@code DATE(column)} or
 * {@code CURDATE()} in SQL. The times in those columns went through the JDBC driver, which
 * shifts them between the application's zone and the connection's on the way in and back
 * out; a date function inside the database would compare against the stored text without
 * that shift and put the boundary eight hours from where it belongs.
 */
public record ReportPeriod(LocalDate start, LocalDate end) {

	public ReportPeriod {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
		if (end.isBefore(start)) {
			throw new BusinessRuleViolation(
					"REPORT_PERIOD_INVALID",
					"A report period cannot end (%s) before it starts (%s)".formatted(end, start));
		}
	}

	/**
	 * The Monday-to-Sunday week that ends on the given day, or most recently before it.
	 *
	 * <p>What the scheduled run reports on. It fires late on Sunday, so on the day it runs this
	 * is the week that is just ending - the use case summarises "本周期的核销记录" on a run
	 * "每周定时任务运行（周日）". Moved to a Monday morning, the same call gives the week that
	 * ended the night before; the answer does not depend on the schedule being kept to the
	 * minute.
	 *
	 * @param day the day the run happens, in the institution's zone
	 * @return the week the run should report on
	 */
	public static ReportPeriod weekEndingOnOrBefore(LocalDate day) {
		LocalDate sunday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
		return new ReportPeriod(sunday.minusDays(6), sunday);
	}

	/** The first moment the period covers: midnight at the start of its first day. */
	public LocalDateTime startsAt() {
		return start.atStartOfDay();
	}

	/** The first moment after the period: midnight at the end of its last day. Exclusive. */
	public LocalDateTime endsBefore() {
		return end.plusDays(1).atStartOfDay();
	}
}
