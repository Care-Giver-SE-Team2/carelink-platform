package sg.nus.carelink.careplan.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Cross-module contract for UC-MG03: the weekly schedule each version of an elder's care plan
 * puts in force, for rostering to turn into dated visits, and for profile to show the family
 * what was planned. Other modules import this interface only, never careplan's domain or
 * infrastructure.
 */
public interface CarePlanSchedules {

	/**
	 * Every version of the elder's plan that has left draft, lowest version first, each with
	 * the days it covers. Versions that ended (stopped, or superseded by a successor's start
	 * date) are included so their visits past that date can be cancelled.
	 */
	List<PlanSchedule> forElder(Long elderId);

	/** Elders with at least one plan that has left draft. */
	List<Long> eldersWithSchedules();

	/**
	 * One plan version's schedule. {@code effectiveUntil} is exclusive and null while the
	 * version is the elder's open-ended current plan.
	 */
	record PlanSchedule(Long carePlanId, Long elderId, int version, LocalDate effectiveFrom,
			LocalDate effectiveUntil, List<Task> tasks) {
	}

	/**
	 * A plan task: its sub-plan label, the care activity it delivers (a catalog code, or null for a
	 * task outside the catalog), its name and the days it recurs on.
	 */
	record Task(Long carePlanNodeId, String groupName, String activityCode, String name, List<Slot> slots) {
	}

	/** One recurring day of a task: Monday at 08:00 for 30 minutes. */
	record Slot(DayOfWeek day, LocalTime startTime, int minutes) {
	}
}
