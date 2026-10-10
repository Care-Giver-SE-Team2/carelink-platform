package sg.nus.carelink.profile.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import sg.nus.carelink.careplan.application.CarePlanSchedules;
import sg.nus.carelink.careplan.application.CarePlanSchedules.PlanSchedule;

/**
 * The family's read-only view of an elder's care plan: the final details of the version in force
 * and of one published to start later. Drafts are never shown; superseded and stopped versions
 * are history the family doesn't need here. Every read is access-checked and audited.
 */
@Service
public class FamilyCarePlanService {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

	private final CarePlanSchedules schedules;
	private final FamilyAccessQuery access;
	private final FamilyReadAudit audit;
	private final Clock clock;

	public FamilyCarePlanService(CarePlanSchedules schedules, FamilyAccessQuery access, FamilyReadAudit audit,
			Clock clock) {
		this.schedules = schedules;
		this.access = access;
		this.audit = audit;
		this.clock = clock;
	}

	public FamilyCarePlan view(String username, Long elderId) {
		return audit.read(username, FamilyReadAudit.Resource.CARE_PLAN, elderId, "", () -> {
			access.requireReadableElder(username, elderId);
			LocalDate today = LocalDate.now(clock.withZone(SINGAPORE));
			List<PlanSchedule> versions = schedules.forElder(elderId);
			PlanSchedule current = versions.stream()
					.filter(plan -> !plan.effectiveFrom().isAfter(today)
							&& (plan.effectiveUntil() == null || plan.effectiveUntil().isAfter(today)))
					.max(Comparator.comparingInt(PlanSchedule::version))
					.orElse(null);
			PlanSchedule upcoming = versions.stream()
					.filter(plan -> plan.effectiveFrom().isAfter(today)
							&& (plan.effectiveUntil() == null || plan.effectiveUntil().isAfter(plan.effectiveFrom())))
					.max(Comparator.comparingInt(PlanSchedule::version))
					.orElse(null);
			return new FamilyCarePlan(version(current), version(upcoming));
		});
	}

	private static FamilyCarePlan.Version version(PlanSchedule plan) {
		if (plan == null) {
			return null;
		}
		return new FamilyCarePlan.Version(plan.version(), plan.effectiveFrom(), plan.effectiveUntil(),
				plan.tasks().stream()
						.map(task -> new FamilyCarePlan.Task(task.groupName(), task.activityCode(), task.name(),
								task.slots().stream()
										.map(slot -> new FamilyCarePlan.Slot(slot.day(), slot.startTime(), slot.minutes()))
										.toList()))
						.toList());
	}
}
