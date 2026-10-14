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

	/** Visits start, and last, in blocks of this many minutes. */
	public static final int STEP_MINUTES = 30;

	/**
	 * The longest visit a plan may be published with. Every catalog activity is a home visit of
	 * an hour or two, and half of rostering's default 8-hour daily cap keeps any one visit
	 * coverable by a replacement who already has other work that day.
	 */
	public static final int MAX_MINUTES = 240;

	public ScheduledVisit {
		Objects.requireNonNull(day, "day");
		Objects.requireNonNull(startTime, "startTime");
		if (minutes < 1) {
			throw new IllegalArgumentException("minutes must be at least 1, got " + minutes);
		}
	}

	/**
	 * True when the visit starts on the hour or half hour: the only start times a plan may be
	 * published with. Rows saved before the rule keep their times, so it is not checked here.
	 */
	public boolean startsOnHalfHour() {
		return startTime.getMinute() % STEP_MINUTES == 0 && startTime.getSecond() == 0 && startTime.getNano() == 0;
	}

	/**
	 * True when the visit lasts a whole number of 30-minute blocks, up to {@link #MAX_MINUTES}: the
	 * only lengths a plan may be published with. Like the start time, not checked on existing rows.
	 */
	public boolean hasAllowedDuration() {
		return minutes % STEP_MINUTES == 0 && minutes <= MAX_MINUTES;
	}

	/**
	 * True when the visit is over by midnight (ending exactly at midnight is fine), so it stays on
	 * its own day. Like the other publish rules, not checked on existing rows.
	 */
	public boolean endsByMidnight() {
		return startTime.toSecondOfDay() + minutes * 60L <= 24 * 60 * 60;
	}

	/**
	 * True when both visits run on the same day at overlapping times (back to back is fine): one
	 * caregiver can't do both. Visits are over by midnight, so the day alone says which day each
	 * falls on.
	 */
	public boolean overlaps(ScheduledVisit other) {
		long start = startTime.toSecondOfDay();
		long otherStart = other.startTime.toSecondOfDay();
		return day.equals(other.day) && start < otherStart + other.minutes * 60L && otherStart < start + minutes * 60L;
	}

	/** The visit's end: start + minutes, wrapping past midnight for a row saved before that rule. */
	public LocalTime endTime() {
		return startTime.plusMinutes(minutes);
	}
}
