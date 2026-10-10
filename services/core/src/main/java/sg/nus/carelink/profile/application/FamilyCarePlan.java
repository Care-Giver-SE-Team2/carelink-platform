package sg.nus.carelink.profile.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * What the family sees of an elder's care plan: the version in force today and, when the manager
 * has published a replacement that hasn't started yet, that one too. Either may be null.
 */
public record FamilyCarePlan(Version current, Version upcoming) {

	/** One issued version. {@code effectiveUntil} is exclusive and null while open-ended. */
	public record Version(int version, LocalDate effectiveFrom, LocalDate effectiveUntil, List<Task> tasks) {
	}

	/** A task as the family reads it: its sub-plan, the catalog activity it delivers, and when it happens. */
	public record Task(String groupName, String activityCode, String name, List<Slot> slots) {
	}

	/** Monday at 08:00 for 30 minutes. */
	public record Slot(DayOfWeek day, LocalTime startTime, int minutes) {
	}
}
