package sg.nus.carelink.rostering.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-MG03: one care plan version's weekly pattern, as rostering sees it. The plan says "Mon and
 * Wed at 08:00 for 30 minutes, from 1 Oct"; this turns that into the dated visits a window of
 * days needs, and says from when the version's remaining visits must be called off.
 *
 * <p>{@code effectiveUntil} is exclusive and null while the version is the elder's current,
 * open-ended plan.
 */
public record RecurringSchedule(Long carePlanId, Long elderId, LocalDate effectiveFrom, LocalDate effectiveUntil,
		List<RecurringTask> tasks) {

	public RecurringSchedule {
		Objects.requireNonNull(carePlanId, "carePlanId");
		Objects.requireNonNull(elderId, "elderId");
		Objects.requireNonNull(effectiveFrom, "effectiveFrom");
		tasks = List.copyOf(tasks);
	}

	/**
	 * Every visit this version calls for that starts at or after {@code from} and on a day
	 * before {@code untilDay}, within the days the version is in force. Earliest first.
	 */
	public List<VisitSlot> visitsBetween(LocalDateTime from, LocalDate untilDay) {
		LocalDate first = later(from.toLocalDate(), effectiveFrom);
		LocalDate end = effectiveUntil == null || untilDay.isBefore(effectiveUntil) ? untilDay : effectiveUntil;
		List<VisitSlot> slots = new ArrayList<>();
		for (LocalDate day = first; day.isBefore(end); day = day.plusDays(1)) {
			slots.addAll(visitsOn(day, from));
		}
		slots.sort(Comparator.comparing(VisitSlot::start).thenComparing(VisitSlot::carePlanNodeId));
		return slots;
	}

	/** The visits one day calls for, leaving out any that start before {@code from}. */
	private List<VisitSlot> visitsOn(LocalDate day, LocalDateTime from) {
		return tasks.stream()
				.flatMap(task -> task.slots().stream()
						.filter(weekly -> weekly.day().equals(day.getDayOfWeek()))
						.map(weekly -> {
							LocalDateTime start = day.atTime(weekly.startTime());
							return new VisitSlot(carePlanId, task.carePlanNodeId(), elderId, task.serviceType(),
									start, start.plusMinutes(weekly.minutes()));
						}))
				.filter(slot -> !slot.start().isBefore(from))
				.toList();
	}

	/**
	 * From when this version's visits that nobody has started must be called off: the day it
	 * stopped or was replaced, but never earlier than {@code now}, so a visit that should have
	 * happened already is left for the missed-check-in rule instead of quietly disappearing.
	 * Empty while the version is still open-ended.
	 */
	public Optional<LocalDateTime> callOffFrom(LocalDateTime now) {
		if (effectiveUntil == null) {
			return Optional.empty();
		}
		LocalDateTime ended = effectiveUntil.atStartOfDay();
		return Optional.of(ended.isAfter(now) ? ended : now);
	}

	private static LocalDate later(LocalDate a, LocalDate b) {
		return a.isAfter(b) ? a : b;
	}

	/** A plan task and the days it recurs on. */
	public record RecurringTask(Long carePlanNodeId, String serviceType, List<WeeklySlot> slots) {

		/** visit.service_type holds 50 characters; a task name may be longer. */
		static final int SERVICE_TYPE_LENGTH = 50;

		public RecurringTask {
			Objects.requireNonNull(carePlanNodeId, "carePlanNodeId");
			if (serviceType != null && serviceType.length() > SERVICE_TYPE_LENGTH) {
				serviceType = serviceType.substring(0, SERVICE_TYPE_LENGTH - 1) + "…";
			}
			slots = List.copyOf(slots);
		}
	}

	/** One recurring day of a task: Monday at 08:00 for 30 minutes. */
	public record WeeklySlot(DayOfWeek day, LocalTime startTime, int minutes) {

		public WeeklySlot {
			Objects.requireNonNull(day, "day");
			Objects.requireNonNull(startTime, "startTime");
			if (minutes < 1) {
				throw new IllegalArgumentException("minutes must be at least 1, got " + minutes);
			}
		}
	}

	/** One dated visit a task calls for. */
	public record VisitSlot(Long carePlanId, Long carePlanNodeId, Long elderId, String serviceType,
			LocalDateTime start, LocalDateTime end) {
	}
}
