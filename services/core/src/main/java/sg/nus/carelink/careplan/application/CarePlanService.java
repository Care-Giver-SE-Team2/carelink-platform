package sg.nus.carelink.careplan.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private final ApplicationEventPublisher events;

	public CarePlanService(CarePlanRepository carePlans, CarePlanNodeRepository carePlanNodes,
			ApplicationEventPublisher events) {
		this.carePlans = carePlans;
		this.carePlanNodes = carePlanNodes;
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
	 * Publishes a draft — replaces its task list with the one the manager just built in
	 * the editor, rolls up total_hours from it, and marks the plan it supersedes (if any) as
	 * superseded. The list is a snapshot at the moment of publishing, not incrementally saved, so
	 * the whole thing is replaced rather than diffed.
	 */
	public CarePlan publish(Long planId, LocalDate startDate, List<PlanNodeInput> nodes) {
		CarePlan plan = carePlans.findById(planId)
				.orElseThrow(() -> new ResourceNotFound("CarePlan", planId));

		carePlanNodes.deleteByCarePlanId(planId);
		BigDecimal totalHours = BigDecimal.ZERO;
		int order = 0;
		for (PlanNodeInput node : nodes) {
			totalHours = totalHours.add(saveTask(planId, node, order++));
		}

		CarePlan published = carePlans.save(plan.publish(startDate, totalHours));

		if (plan.supersedesPlanId() != null) {
			carePlans.findById(plan.supersedesPlanId())
					.filter(previous -> previous.status() == CarePlan.Status.PUBLISHED)
					.ifPresent(previous -> carePlans.save(previous.supersede()));
		}

		events.publishEvent(new CarePlanScheduleChanged(published.elderId()));
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

	private BigDecimal saveTask(Long planId, PlanNodeInput node, int displayOrder) {
		if (node.visits() == null || node.visits().isEmpty()) {
			throw new BusinessRuleViolation(
					"CARE_PLAN_TASK_NO_VISITS", "Task [%s] has no scheduled visits".formatted(node.name()));
		}
		List<ScheduledVisit> visits = scheduledVisitsOf(node);
		int totalMinutes = visits.stream().mapToInt(ScheduledVisit::minutes).sum();
		// duration_per_visit is kept as the average for readers that predate the per-day rows.
		int averageMinutes = Math.round((float) totalMinutes / visits.size());
		CarePlanNode task = new CarePlanNode(
				null, planId, node.groupName(), node.name(),
				scheduleDaysOf(node.visits()), minutesToHours(averageMinutes), minutesToHours(totalMinutes),
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
