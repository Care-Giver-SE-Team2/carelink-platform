package sg.nus.carelink.rostering.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.careplan.application.CarePlanSchedules;
import sg.nus.carelink.profile.application.PrimaryCaregiverLookup;
import sg.nus.carelink.rostering.domain.model.RecurringSchedule;
import sg.nus.carelink.rostering.domain.model.RecurringSchedule.RecurringTask;
import sg.nus.carelink.rostering.domain.model.RecurringSchedule.WeeklySlot;
import sg.nus.carelink.visit.application.VisitScheduling;
import sg.nus.carelink.visit.application.VisitScheduling.PlannedVisit;

/**
 * UC-MG03: keeps an elder's visits in step with their care plan. Publishing a plan sets the
 * weekly pattern; this turns it into dated visits for the next {@code horizonDays} days,
 * given to the elder's primary caregiver, or left unassigned to be covered when there is none.
 *
 * <p>A refresh is safe to repeat. It runs when a plan is published or stopped, when a primary
 * caregiver is named, and nightly to roll the window forward. When a plan version ends
 * (stopped, or replaced from its successor's start date) its visits nobody has started are
 * cancelled from that date; visits under way or finished are kept.
 */
@Service
@Transactional
public class RecurringRosterService {

	private final CarePlanSchedules schedules;
	private final PrimaryCaregiverLookup primaryCaregivers;
	private final VisitScheduling visits;
	private final Clock clock;
	private final int horizonDays;

	public RecurringRosterService(CarePlanSchedules schedules, PrimaryCaregiverLookup primaryCaregivers,
			VisitScheduling visits, Clock clock, @Value("${carelink.roster.horizon-days:14}") int horizonDays) {
		this.schedules = schedules;
		this.primaryCaregivers = primaryCaregivers;
		this.visits = visits;
		this.clock = clock;
		this.horizonDays = horizonDays;
	}

	/**
	 * Brings one elder's visits for today and the next {@code horizonDays - 1} days in line
	 * with their plan. Always its own transaction: it is called after a publish has committed,
	 * and once per elder by the nightly run, and in both cases a failure must stay contained
	 * to this elder's refresh.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public RosterRefresh refreshElder(Long elderId) {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate untilDay = now.toLocalDate().plusDays(horizonDays);
		List<RecurringSchedule> versions = schedules.forElder(elderId).stream()
				.map(RecurringRosterService::toDomain)
				.toList();

		int cancelled = 0;
		for (RecurringSchedule version : versions) {
			cancelled += version.callOffFrom(now)
					.map(from -> visits.cancelUntouchedFrom(version.carePlanId(), from))
					.orElse(0);
		}

		Long caregiverId = primaryCaregivers.findRosterableCaregiverId(elderId).orElse(null);
		List<PlannedVisit> wanted = versions.stream()
				.flatMap(version -> version.visitsBetween(now, untilDay).stream())
				.map(slot -> new PlannedVisit(slot.elderId(), caregiverId, slot.carePlanId(), slot.carePlanNodeId(),
						slot.serviceType(), slot.start(), slot.end()))
				.toList();
		VisitScheduling.Outcome outcome = wanted.isEmpty()
				? new VisitScheduling.Outcome(0, 0)
				: visits.schedule(wanted);

		return new RosterRefresh(elderId, outcome.created(), outcome.covered(), cancelled);
	}

	/** Every elder whose plan has ever been published: the nightly run refreshes each in turn. */
	@Transactional(readOnly = true)
	public List<Long> eldersToRefresh() {
		return schedules.eldersWithSchedules();
	}

	private static RecurringSchedule toDomain(CarePlanSchedules.PlanSchedule plan) {
		return new RecurringSchedule(plan.carePlanId(), plan.elderId(), plan.effectiveFrom(), plan.effectiveUntil(),
				plan.tasks().stream()
						.map(task -> new RecurringTask(task.carePlanNodeId(), task.name(), task.slots().stream()
								.map(slot -> new WeeklySlot(slot.day(), slot.startTime(), slot.minutes()))
								.toList()))
						.toList());
	}

	/** What one refresh changed. */
	public record RosterRefresh(Long elderId, int created, int covered, int cancelled) {
	}
}
