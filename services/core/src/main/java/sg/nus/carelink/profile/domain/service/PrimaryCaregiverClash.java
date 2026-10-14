package sg.nus.carelink.profile.domain.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * UC-MG02: whether naming a caregiver primary caregiver of one more elder would put two of their
 * standing visits at the same time. Only the care plans of elders they are already primary
 * caregiver of count: covers and extra services come and go, and rostering's replacement search
 * checks those visit by visit.
 *
 * <p>Two weekly slots clash when they overlap in time, or leave less than the travel buffer
 * between one ending and the other starting, since they are at different elders' homes (a visit
 * running past midnight carries into the next day), on a weekday that falls while both plan
 * versions are in force, from today on.
 */
public final class PrimaryCaregiverClash {

	private static final int MINUTES_PER_DAY = 24 * 60;
	private static final int MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY;

	private PrimaryCaregiverClash() {
	}

	/**
	 * One plan version of an elder: the days it is in force ({@code until} exclusive, null while
	 * open-ended) and its weekly slots.
	 */
	public record Plan(Long elderId, LocalDate from, LocalDate until, List<Slot> slots) {

		public Plan {
			slots = List.copyOf(slots);
		}
	}

	/** One recurring visit of a plan task: Monday at 08:00 for 30 minutes. */
	public record Slot(String task, DayOfWeek day, LocalTime start, int minutes) {

		public LocalTime end() {
			return start.plusMinutes(minutes);
		}

		private int startOfWeek() {
			return (day.getValue() - 1) * MINUTES_PER_DAY + start.toSecondOfDay() / 60;
		}
	}

	/**
	 * A slot of the plan the caregiver would take on, and the slot in another elder's plan it
	 * lands on ({@code overlapping}) or leaves too little time to travel from or to.
	 */
	public record Clash(Slot wanted, Long heldElderId, Slot held, boolean overlapping) {
	}

	/**
	 * The first clash between the plans the caregiver would take on and the plans they already
	 * hold, earliest plan and slot first, so the same pick is always refused for the same reason.
	 *
	 * @param travelBufferMinutes the least time between visits at two elders' homes; 0 allows
	 *        back to back
	 */
	public static Optional<Clash> firstClash(List<Plan> wanted, List<Plan> held, LocalDate today,
			int travelBufferMinutes) {
		Comparator<Slot> weekOrder = Comparator.comparingInt(Slot::startOfWeek);
		for (Plan mine : wanted.stream().sorted(Comparator.comparing(Plan::from)).toList()) {
			for (Plan theirs : held.stream().sorted(Comparator.comparing(Plan::from)).toList()) {
				LocalDate start = latest(today, mine.from(), theirs.from());
				LocalDate end = earliest(mine.until(), theirs.until());
				if (end != null && !start.isBefore(end)) {
					continue;
				}
				for (Slot a : mine.slots().stream().sorted(weekOrder).toList()) {
					for (Slot b : theirs.slots().stream().sorted(weekOrder).toList()) {
						if (overlaps(a, b, travelBufferMinutes) && occurs(a.day(), start, end)
								&& occurs(b.day(), start, end)) {
							return Optional.of(new Clash(a, theirs.elderId(), b, overlaps(a, b, 0)));
						}
					}
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * Overlap on the weekly clock, where Sunday night runs into Monday morning, with each visit
	 * stretched by {@code buffer} minutes so too short a gap counts too.
	 */
	private static boolean overlaps(Slot a, Slot b, int buffer) {
		int aStart = a.startOfWeek();
		int aEnd = aStart + a.minutes();
		for (int shift : new int[] {-MINUTES_PER_WEEK, 0, MINUTES_PER_WEEK}) {
			int bStart = b.startOfWeek() + shift;
			if (aStart < bStart + b.minutes() + buffer && bStart < aEnd + buffer) {
				return true;
			}
		}
		return false;
	}

	/** Whether the weekday falls in [start, end); a week or more always holds every weekday. */
	private static boolean occurs(DayOfWeek day, LocalDate start, LocalDate end) {
		if (end == null || ChronoUnit.DAYS.between(start, end) >= 7) {
			return true;
		}
		for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
			if (date.getDayOfWeek() == day) {
				return true;
			}
		}
		return false;
	}

	private static LocalDate latest(LocalDate a, LocalDate b, LocalDate c) {
		LocalDate later = a.isAfter(b) ? a : b;
		return later.isAfter(c) ? later : c;
	}

	private static LocalDate earliest(LocalDate a, LocalDate b) {
		if (a == null) {
			return b;
		}
		return b == null || a.isBefore(b) ? a : b;
	}
}
