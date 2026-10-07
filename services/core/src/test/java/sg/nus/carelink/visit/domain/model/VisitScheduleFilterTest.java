package sg.nus.carelink.visit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Checks calendar boundaries and valid schedule filter limits.
 *
 * @author Wang Zhili
 */
class VisitScheduleFilterTest {

	@ParameterizedTest
	@CsvSource({
			"2026-09-28,2026-09-28,2026-10-05",
			"2026-10-04,2026-09-28,2026-10-05",
			"2027-01-01,2026-12-28,2027-01-04"
	})
	void omittedDatesSelectTheWholeCalendarWeek(String today, String first, String afterLast) {
		var filter = new VisitScheduleFilter(null, null, null, null, null, 0, 20);
		var dates = filter.dateRange(LocalDate.parse(today));
		assertThat(dates.fromInclusive()).isEqualTo(LocalDate.parse(first).atStartOfDay());
		assertThat(dates.toExclusive()).isEqualTo(LocalDate.parse(afterLast).atStartOfDay());
	}

	@ParameterizedTest
	@CsvSource({ "2024-02-29,2024-03-01", "9999-12-30,9999-12-31", "1000-01-01,1000-01-02" })
	void includesTheEntireLastDay(String selected, String afterLast) {
		LocalDate date = LocalDate.parse(selected);
		var filter = new VisitScheduleFilter(1L, 2L, date, date, Visit.Status.SCHEDULED, 0, 200);
		var dates = filter.dateRange(LocalDate.of(2026, 1, 1));
		assertThat(dates.fromInclusive()).isEqualTo(date.atStartOfDay());
		assertThat(dates.toExclusive()).isEqualTo(LocalDate.parse(afterLast).atStartOfDay());
	}

	@ParameterizedTest
	@CsvSource({ ",2026-09-23", "2026-09-23,", "2026-09-24,2026-09-23",
			"0999-12-31,2026-09-23", "9999-12-30,9999-12-31" })
	void rejectsMissingReversedOrUnsupportedDates(String first, String last) {
		LocalDate from = first == null ? null : LocalDate.parse(first);
		LocalDate to = last == null ? null : LocalDate.parse(last);
		assertThat(VisitScheduleFilter.validDates(from, to)).isFalse();
		assertThatThrownBy(() -> new VisitScheduleFilter(null, null, from, to, null, 0, 20))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@CsvSource({ "-1,20", "0,0", "0,201" })
	void rejectsInvalidPagination(int page, int size) {
		assertThatThrownBy(() -> new VisitScheduleFilter(null, null, null, null, null, page, size))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@CsvSource({ "0,", "-1,", ",0", ",-1" })
	void rejectsInvalidProfileIds(Long elderId, Long caregiverId) {
		assertThatThrownBy(() -> new VisitScheduleFilter(elderId, caregiverId, null, null, null, 0, 20))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void acceptsSingleItemPagesAndLargePageNumbers() {
		var filter = new VisitScheduleFilter(null, null, null, null, null, Integer.MAX_VALUE, 1);
		assertThat(filter.page()).isEqualTo(Integer.MAX_VALUE);
		assertThat(filter.size()).isEqualTo(1);
	}
}
