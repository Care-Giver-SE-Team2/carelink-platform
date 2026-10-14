package sg.nus.carelink.careplan.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.careplan.domain.model.CareActivity;
import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.domain.model.CarePlanNode;
import sg.nus.carelink.careplan.domain.model.ScheduleDays;
import sg.nus.carelink.careplan.domain.model.ScheduledVisit;
import sg.nus.carelink.careplan.domain.repository.CarePlanNodeRepository;
import sg.nus.carelink.careplan.domain.repository.CarePlanRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * Application layer of the careplan module (care plans, the plan node tree and the credentials a plan requires).
 *
 * <p>One public method per use case (UC-MG01): it loads what it needs through
 * the domain ports, calls the domain model, saves, and returns. Business rules stay in
 * domain.model. identity.application.IdentityService is the template.
 */
@Service
@Transactional
public class CarePlanService {

	private final CarePlanRepository carePlans;
	private final CarePlanNodeRepository carePlanNodes;
	private final PrimaryCaregiverClashCheck primaryCaregiverClashes;
	private final ApplicationEventPublisher events;

	public CarePlanService(CarePlanRepository carePlans, CarePlanNodeRepository carePlanNodes,
			PrimaryCaregiverClashCheck primaryCaregiverClashes, ApplicationEventPublisher events) {
		this.carePlans = carePlans;
		this.carePlanNodes = carePlanNodes;
		this.primaryCaregiverClashes = primaryCaregiverClashes;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public Optional<CarePlan> findCarePlan(Long id) {
		return carePlans.findById(id);
	}

	/** The elder's highest-version plan (draft, published or superseded), if any. */
	@Transactional(readOnly = true)
	public Optional<CarePlan> findLatestByElderId(Long elderId) {
		return carePlans.findLatestByElderId(elderId);
	}

	/** Every version of the elder's plan, drafts included, newest first — the editor's version history. */
	@Transactional(readOnly = true)
	public List<CarePlan> findVersions(Long elderId) {
		return carePlans.findByElderId(elderId).reversed();
	}

	@Transactional(readOnly = true)
	public List<CarePlanNode> findNodes(Long carePlanId) {
		return carePlanNodes.findByCarePlanId(carePlanId);
	}

	/** Opens an empty draft plan for an elder. See CarePlan.startDraft for the rules. */
	public CarePlan createDraft(Long elderId, Long createdByUserId) {
		CarePlan latest = carePlans.findLatestByElderId(elderId).orElse(null);
		CarePlan draft = CarePlan.startDraft(elderId, createdByUserId, latest);
		return carePlans.save(draft);
	}

	/**
	 * Saves the manager's work in progress on a draft, so leaving the editor doesn't lose it:
	 * the start date so far and the whole task list, replaced rather than diffed. Unlike publish,
	 * a task may have no visits yet (the family chose the activity, the manager hasn't scheduled
	 * it), a visit may start off the half hour, last other than 30-minute blocks or run past
	 * midnight (copied from a plan saved before those rules) and the start date may be empty. Nothing is scheduled from a draft — rostering and visits
	 * only read issued versions — so no event is raised.
	 */
	public CarePlan saveDraft(Long planId, LocalDate startDate, List<PlanNodeInput> nodes) {
		CarePlan plan = carePlans.findById(planId)
				.orElseThrow(() -> new ResourceNotFound("CarePlan", planId));
		CarePlan revised = carePlans.save(plan.reviseDraft(startDate));
		replaceTasks(planId, nodes, false);
		return revised;
	}

	/**
	 * Throws a draft away with its tasks. The version it would have replaced stays in force; an
	 * elder with no issued version is left with no plan.
	 */
	public void discardDraft(Long planId) {
		CarePlan plan = carePlans.findById(planId)
				.orElseThrow(() -> new ResourceNotFound("CarePlan", planId));
		plan.discard();
		carePlanNodes.deleteByCarePlanId(planId);
		carePlans.deleteById(planId);
	}

	/**
	 * Publishes a draft — replaces its task list with the one the manager just built in
	 * the editor, rolls up total_hours from it, and marks the plan it supersedes (if any) as
	 * superseded. The list sent here is the final word: whatever the draft last saved is
	 * replaced rather than diffed. Refused, and nothing kept, when a visit would fall at the same
	 * time as one in the plan of another elder the elder's primary caregiver looks after.
	 */
	public CarePlan publish(Long planId, LocalDate startDate, List<PlanNodeInput> nodes) {
		CarePlan plan = carePlans.findById(planId)
				.orElseThrow(() -> new ResourceNotFound("CarePlan", planId));

		BigDecimal totalHours = replaceTasks(planId, nodes, true);
		CarePlan publishing = plan.publish(startDate, totalHours);
		primaryCaregiverClashes.clashFor(plan.elderId(), startDate, carePlanNodes.findByCarePlanId(planId).stream()
				.map(CarePlanSchedulesService::taskOf)
				.toList())
				.ifPresent(reason -> {
					throw new BusinessRuleViolation("CAREGIVER_TIME_CLASH", reason);
				});

		CarePlan published = carePlans.save(publishing);

		if (plan.supersedesPlanId() != null) {
			carePlans.findById(plan.supersedesPlanId())
					.filter(previous -> previous.status() == CarePlan.Status.PUBLISHED)
					.ifPresent(previous -> carePlans.save(previous.supersede()));
		}

		events.publishEvent(new CarePlanScheduleChanged(published.elderId()));
		events.publishEvent(new CarePlanPublished(
				published.elderId(), published.id(), published.version(), published.startDate()));
		return published;
	}

	/** Ends an active plan early. See CarePlan.stop for the rules. */
	public CarePlan stop(Long planId, LocalDate effectiveDate, String reason, Long stoppedByUserId) {
		CarePlan plan = carePlans.findById(planId)
				.orElseThrow(() -> new ResourceNotFound("CarePlan", planId));
		CarePlan stopped = carePlans.save(plan.stop(effectiveDate, reason, stoppedByUserId));
		events.publishEvent(new CarePlanScheduleChanged(stopped.elderId()));
		return stopped;
	}

	/**
	 * Replaces a plan's tasks with {@code nodes}, in order, and returns their total weekly hours.
	 * Publishing also requires every task to have visits, each starting on the hour or half hour
	 * and lasting 30-minute blocks up to ScheduledVisit.MAX_MINUTES, over by midnight, and no two
	 * tasks to overlap: each becomes a visit of its own, for the same caregiver.
	 */
	private BigDecimal replaceTasks(Long planId, List<PlanNodeInput> nodes, boolean publishing) {
		carePlanNodes.deleteByCarePlanId(planId);
		BigDecimal totalHours = BigDecimal.ZERO;
		int order = 0;
		for (PlanNodeInput node : nodes) {
			totalHours = totalHours.add(saveTask(planId, node, order++, publishing));
		}
		if (publishing) {
			requireNoOverlappingTasks(carePlanNodes.findByCarePlanId(planId));
		}
		return totalHours;
	}

	/**
	 * Refuses two tasks whose visits overlap on the same day. Each task becomes a visit of its own
	 * for the elder's primary caregiver, so overlapping tasks would book them twice at once; tasks
	 * meant to be done together are staggered, or made one task.
	 */
	private static void requireNoOverlappingTasks(List<CarePlanNode> tasks) {
		for (int i = 0; i < tasks.size(); i++) {
			for (int j = i + 1; j < tasks.size(); j++) {
				CarePlanNode first = tasks.get(i);
				CarePlanNode second = tasks.get(j);
				for (ScheduledVisit a : first.visits()) {
					for (ScheduledVisit b : second.visits()) {
						if (a.overlaps(b)) {
							throw new BusinessRuleViolation("CARE_PLAN_TASKS_OVERLAP",
									"Tasks [%s] (%s) and [%s] (%s) overlap on %s; one caregiver can't do both, so "
											.formatted(first.name(), range(a), second.name(), range(b),
													a.day().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
											+ "stagger them or make them one task");
						}
					}
				}
			}
		}
	}

	private static String range(ScheduledVisit visit) {
		return visit.startTime() + "–" + visit.endTime();
	}

	private BigDecimal saveTask(Long planId, PlanNodeInput node, int displayOrder, boolean publishing) {
		boolean unscheduled = node.visits() == null || node.visits().isEmpty();
		if (unscheduled && publishing) {
			throw new BusinessRuleViolation(
					"CARE_PLAN_TASK_NO_VISITS", "Task [%s] has no scheduled visits".formatted(node.name()));
		}
		CareActivity.requireKnownOrAbsent(node.activityCode());
		List<ScheduledVisit> visits = unscheduled ? List.of() : scheduledVisitsOf(node);
		if (publishing) {
			visits.stream().filter(visit -> !visit.startsOnHalfHour()).findFirst().ifPresent(visit -> {
				throw new BusinessRuleViolation("CARE_PLAN_TASK_START_NOT_ON_HALF_HOUR",
						"Task [%s] starts at %s on %s; visits start on the hour or half hour".formatted(
								node.name(), visit.startTime(), visit.day().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)));
			});
			visits.stream().filter(visit -> !visit.hasAllowedDuration()).findFirst().ifPresent(visit -> {
				throw new BusinessRuleViolation("CARE_PLAN_TASK_DURATION_NOT_ALLOWED",
						"Task [%s] runs %d minutes on %s; visits run 30 minutes to %d hours, in steps of 30 minutes"
								.formatted(node.name(), visit.minutes(),
										visit.day().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
										ScheduledVisit.MAX_MINUTES / 60));
			});
			visits.stream().filter(visit -> !visit.endsByMidnight()).findFirst().ifPresent(visit -> {
				throw new BusinessRuleViolation("CARE_PLAN_TASK_PAST_MIDNIGHT",
						"Task [%s] starts at %s on %s and runs %d minutes, past midnight; visits end by midnight"
								.formatted(node.name(), visit.startTime(),
										visit.day().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), visit.minutes()));
			});
		}
		int totalMinutes = visits.stream().mapToInt(ScheduledVisit::minutes).sum();
		// duration_per_visit is kept as the average for readers that predate the per-day rows.
		BigDecimal averageHours = unscheduled ? null : minutesToHours(Math.round((float) totalMinutes / visits.size()));
		CarePlanNode task = new CarePlanNode(
				null, planId, node.groupName(), node.activityCode(), node.name(),
				unscheduled ? null : scheduleDaysOf(node.visits()), averageHours, minutesToHours(totalMinutes),
				node.evidenceType() == null ? CarePlanNode.EvidenceType.NONE : node.evidenceType(),
				displayOrder, null, null, visits);
		return carePlanNodes.save(task).weeklyHours();
	}

	/** Each day as entered: its own start time and minutes. A day may appear only once per task. */
	private static List<ScheduledVisit> scheduledVisitsOf(PlanNodeInput node) {
		Set<DayOfWeek> seen = new HashSet<>();
		return node.visits().stream().map(visit -> {
			DayOfWeek day = ScheduleDays.dayOf(visit.day());
			if (!seen.add(day)) {
				throw new BusinessRuleViolation("CARE_PLAN_TASK_DUPLICATE_DAY",
						"Task [%s] schedules %s more than once".formatted(node.name(), visit.day()));
			}
			if (visit.startTime() == null) {
				throw new BusinessRuleViolation("CARE_PLAN_TASK_NO_START_TIME",
						"Task [%s] has no start time on %s".formatted(node.name(), visit.day()));
			}
			return new ScheduledVisit(day, visit.startTime(), visit.minutes());
		}).toList();
	}

	private static String scheduleDaysOf(List<VisitInput> visits) {
		EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
		for (VisitInput visit : visits) {
			days.add(ScheduleDays.dayOf(visit.day()));
		}
		return ScheduleDays.encode(days);
	}

	private static BigDecimal minutesToHours(int minutes) {
		return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
	}
}
