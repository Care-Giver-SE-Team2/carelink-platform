package sg.nus.carelink.careplan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.careplan.domain.model.ScheduledVisit;

class ScheduledVisitTest {

	@Test
	void startsOnTheHalfHourOnlyAtZeroOrThirtyMinutesPast() {
		assertThat(visitAt(LocalTime.of(8, 0)).startsOnHalfHour()).isTrue();
		assertThat(visitAt(LocalTime.of(23, 30)).startsOnHalfHour()).isTrue();
		assertThat(visitAt(LocalTime.MIDNIGHT).startsOnHalfHour()).isTrue();
		assertThat(visitAt(LocalTime.of(8, 15)).startsOnHalfHour()).isFalse();
		assertThat(visitAt(LocalTime.of(8, 30, 1)).startsOnHalfHour()).isFalse();
	}

	@Test
	void lastsWholeThirtyMinuteBlocksUpToFourHours() {
		assertThat(new ScheduledVisit(DayOfWeek.MONDAY, LocalTime.of(8, 0), 30).hasAllowedDuration()).isTrue();
		assertThat(new ScheduledVisit(DayOfWeek.MONDAY, LocalTime.of(8, 0), 240).hasAllowedDuration()).isTrue();
		assertThat(new ScheduledVisit(DayOfWeek.MONDAY, LocalTime.of(8, 0), 15).hasAllowedDuration()).isFalse();
		assertThat(new ScheduledVisit(DayOfWeek.MONDAY, LocalTime.of(8, 0), 45).hasAllowedDuration()).isFalse();
		assertThat(new ScheduledVisit(DayOfWeek.MONDAY, LocalTime.of(8, 0), 270).hasAllowedDuration()).isFalse();
	}

	@Test
	void endsByMidnightUnlessItRunsIntoTheNextDay() {
		assertThat(new ScheduledVisit(DayOfWeek.SATURDAY, LocalTime.of(23, 30), 30).endsByMidnight()).isTrue();
		assertThat(new ScheduledVisit(DayOfWeek.SATURDAY, LocalTime.of(20, 0), 240).endsByMidnight()).isTrue();
		assertThat(new ScheduledVisit(DayOfWeek.SATURDAY, LocalTime.of(23, 30), 60).endsByMidnight()).isFalse();
		assertThat(new ScheduledVisit(DayOfWeek.SATURDAY, LocalTime.of(21, 0), 240).endsByMidnight()).isFalse();
	}

	@Test
	void overlapsAnotherOnlyOnTheSameDayAtOverlappingTimes() {
		ScheduledVisit bathing = new ScheduledVisit(DayOfWeek.MONDAY, LocalTime.of(8, 0), 60);

		assertThat(bathing.overlaps(new ScheduledVisit(DayOfWeek.MONDAY, LocalTime.of(8, 30), 30))).isTrue();
		assertThat(bathing.overlaps(new ScheduledVisit(DayOfWeek.MONDAY, LocalTime.of(7, 30), 60))).isTrue();
		assertThat(bathing.overlaps(new ScheduledVisit(DayOfWeek.MONDAY, LocalTime.of(9, 0), 30))).isFalse();
		assertThat(bathing.overlaps(new ScheduledVisit(DayOfWeek.MONDAY, LocalTime.of(7, 30), 30))).isFalse();
		assertThat(bathing.overlaps(new ScheduledVisit(DayOfWeek.TUESDAY, LocalTime.of(8, 0), 60))).isFalse();
	}

	private static ScheduledVisit visitAt(LocalTime start) {
		return new ScheduledVisit(DayOfWeek.MONDAY, start, 30);
	}
}
