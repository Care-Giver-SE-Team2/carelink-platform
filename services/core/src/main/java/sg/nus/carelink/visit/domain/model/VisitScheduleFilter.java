package sg.nus.carelink.visit.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Filters a family schedule using inclusive local dates and bounded pagination.
 *
 * @author Wang Zhili
 */
public record VisitScheduleFilter(Long elderId, Long caregiverId, LocalDate dateFrom, LocalDate dateTo,
		Visit.Status status, int page, int size) {

	private static final LocalDate MIN_DATE = LocalDate.of(1000, 1, 1);
	private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 30);

	public VisitScheduleFilter {
		if (!validDates(dateFrom, dateTo)) {
			throw new IllegalArgumentException("Dates must form an inclusive range within 1000-01-01 and 9999-12-30");
		}
		if (page < 0 || size < 1 || size > 200) {
			throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 200");
		}
		if ((elderId != null && elderId <= 0) || (caregiverId != null && caregiverId <= 0)) {
			throw new IllegalArgumentException("Profile IDs must be positive");
		}
	}

	/**
	 * Checks whether dates are both absent or form a supported inclusive range.
	 *
	 * @param from First local date, or null
	 * @param to Last local date, or null
	 * @return Whether the pair can be used as a database date range
	 * @author Wang Zhili
	 */
	public static boolean validDates(LocalDate from, LocalDate to) {
		if (from == null || to == null) {
			return from == null && to == null;
		}
		return !from.isBefore(MIN_DATE) && !to.isAfter(MAX_DATE) && !to.isBefore(from);
	}

	/**
	 * Resolves omitted dates to the current Monday-to-Sunday week.
	 *
	 * @param today Current local date in Asia/Singapore
	 * @return Start-inclusive and end-exclusive local timestamp bounds
	 * @author Wang Zhili
	 */
	public DateRange dateRange(LocalDate today) {
		LocalDate first = dateFrom == null
				? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : dateFrom;
		LocalDate afterLast = dateTo == null ? first.plusWeeks(1) : dateTo.plusDays(1);
		return new DateRange(first.atStartOfDay(), afterLast.atStartOfDay());
	}

	public record DateRange(LocalDateTime fromInclusive, LocalDateTime toExclusive) {
	}
}
