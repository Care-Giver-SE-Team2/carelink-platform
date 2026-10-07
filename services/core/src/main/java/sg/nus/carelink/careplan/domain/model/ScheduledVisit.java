package sg.nus.carelink.careplan.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * One day of a task's weekly schedule: the day, when the visit starts, and how long it runs.
 * Each day is set independently, so a task can run Mon at 8:00 for 30 minutes and Wed at
 * 16:30 for 45. The end time is start + minutes and is not stored.
 */
public record ScheduledVisit(DayOfWeek day, LocalTime startTime, int minutes) {

	public ScheduledVisit {
		Objects.requireNonNull(day, "day");
		Objects.requireNonNull(startTime, "startTime");
		if (minutes < 1) {
			throw new IllegalArgumentException("minutes must be at least 1, got " + minutes);
		}
	}
}
