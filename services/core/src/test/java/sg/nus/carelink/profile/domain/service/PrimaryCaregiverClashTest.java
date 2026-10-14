package sg.nus.carelink.profile.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.service.PrimaryCaregiverClash.Clash;
import sg.nus.carelink.profile.domain.service.PrimaryCaregiverClash.Plan;
import sg.nus.carelink.profile.domain.service.PrimaryCaregiverClash.Slot;

class PrimaryCaregiverClashTest {

	// Thursday 1 Oct 2026.
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

	@Test
	void findsTwoVisitsThatOverlapOnTheSameWeekday() {
		Slot bathing = slot("Bathing", DayOfWeek.MONDAY, 8, 0, 30);
		Slot meals = slot("Meals", DayOfWeek.MONDAY, 8, 15, 30);

		assertThat(PrimaryCaregiverClash.firstClash(List.of(plan(1L, TODAY, null, bathing)),
				List.of(plan(2L, TODAY, null, meals)), TODAY, 0))
				.contains(new Clash(bathing, 2L, meals, true));
	}

	@Test
	void withoutATravelBufferBackToBackVisitsAndOtherWeekdaysDoNotClash() {
		Plan wanted = plan(1L, TODAY, null, slot("Bathing", DayOfWeek.MONDAY, 8, 0, 30));

		assertThat(PrimaryCaregiverClash.firstClash(List.of(wanted),
				List.of(plan(2L, TODAY, null, slot("Meals", DayOfWeek.MONDAY, 8, 30, 30),
						slot("Meals", DayOfWeek.TUESDAY, 8, 0, 30))), TODAY, 0))
				.isEmpty();
	}

	@Test
	void aVisitRunningPastMidnightClashesWithOneEarlyNextMorning() {
		Slot lateNight = slot("Night check", DayOfWeek.SUNDAY, 23, 30, 60);
		Slot earlyMonday = slot("Medication", DayOfWeek.MONDAY, 0, 15, 15);

		assertThat(PrimaryCaregiverClash.firstClash(List.of(plan(1L, TODAY, null, lateNight)),
				List.of(plan(2L, TODAY, null, earlyMonday)), TODAY, 0))
				.contains(new Clash(lateNight, 2L, earlyMonday, true));
	}

	@Test
	void plansThatAreNeverInForceTogetherDoNotClash() {
		Slot monday = slot("Bathing", DayOfWeek.MONDAY, 8, 0, 30);

		// The held plan ends the day the wanted one starts.
		assertThat(PrimaryCaregiverClash.firstClash(List.of(plan(1L, TODAY.plusDays(10), null, monday)),
				List.of(plan(2L, TODAY, TODAY.plusDays(10), monday)), TODAY, 0)).isEmpty();
		// The held plan has already ended.
		assertThat(PrimaryCaregiverClash.firstClash(List.of(plan(1L, TODAY, null, monday)),
				List.of(plan(2L, TODAY.minusDays(30), TODAY, monday)), TODAY, 0)).isEmpty();
	}

	@Test
	void aShortOverlapClashesOnlyIfTheWeekdayFallsInIt() {
		Slot monday = slot("Bathing", DayOfWeek.MONDAY, 8, 0, 30);
		// Both in force Thu 1 to Sat 3 Oct: no Monday in it.
		List<Plan> held = List.of(plan(2L, TODAY.minusDays(30), TODAY.plusDays(3), monday));

		assertThat(PrimaryCaregiverClash.firstClash(List.of(plan(1L, TODAY, null, monday)), held, TODAY, 0)).isEmpty();
		// Until Tue 6 Oct: Monday 5 Oct falls in it.
		assertThat(PrimaryCaregiverClash.firstClash(List.of(plan(1L, TODAY, null, monday)),
				List.of(plan(2L, TODAY.minusDays(30), TODAY.plusDays(5), monday)), TODAY, 0)).isPresent();
	}

	@Test
	void anUpcomingPlanClashesFromItsStartDate() {
		Slot monday = slot("Bathing", DayOfWeek.MONDAY, 8, 0, 30);

		assertThat(PrimaryCaregiverClash.firstClash(List.of(plan(1L, TODAY.plusDays(20), null, monday)),
				List.of(plan(2L, TODAY.minusDays(30), null, monday)), TODAY, 0)).isPresent();
	}

	@Test
	void aGapShorterThanTheTravelBufferClashesWithoutOverlapping() {
		Slot bathing = slot("Bathing", DayOfWeek.MONDAY, 8, 0, 30);
		Slot meals = slot("Meals", DayOfWeek.MONDAY, 8, 30, 30);

		assertThat(PrimaryCaregiverClash.firstClash(List.of(plan(1L, TODAY, null, bathing)),
				List.of(plan(2L, TODAY, null, meals)), TODAY, 30))
				.contains(new Clash(bathing, 2L, meals, false));
		// Before it, too: meals at 07:00-07:30 leaves bathing at 08:00 a 30-minute gap, which is enough.
		assertThat(PrimaryCaregiverClash.firstClash(List.of(plan(1L, TODAY, null, bathing)),
				List.of(plan(2L, TODAY, null, slot("Meals", DayOfWeek.MONDAY, 7, 0, 30))), TODAY, 30))
				.isEmpty();
		assertThat(PrimaryCaregiverClash.firstClash(List.of(plan(1L, TODAY, null, bathing)),
				List.of(plan(2L, TODAY, null, slot("Meals", DayOfWeek.MONDAY, 7, 30, 30))), TODAY, 30))
				.isPresent();
	}

	@Test
	void theTravelBufferCarriesOverMidnight() {
		Slot lateSunday = slot("Night check", DayOfWeek.SUNDAY, 23, 30, 30);
		Slot earlyMonday = slot("Medication", DayOfWeek.MONDAY, 0, 0, 30);

		assertThat(PrimaryCaregiverClash.firstClash(List.of(plan(1L, TODAY, null, lateSunday)),
				List.of(plan(2L, TODAY, null, earlyMonday)), TODAY, 30))
				.contains(new Clash(lateSunday, 2L, earlyMonday, false));
	}

	private static Plan plan(Long elderId, LocalDate from, LocalDate until, Slot... slots) {
		return new Plan(elderId, from, until, List.of(slots));
	}

	private static Slot slot(String task, DayOfWeek day, int hour, int minute, int minutes) {
		return new Slot(task, day, LocalTime.of(hour, minute), minutes);
	}
}
