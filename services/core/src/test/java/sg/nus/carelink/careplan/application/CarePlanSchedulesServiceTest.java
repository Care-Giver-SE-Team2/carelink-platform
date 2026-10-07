package sg.nus.carelink.careplan.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.careplan.application.CarePlanSchedules.PlanSchedule;
import sg.nus.carelink.careplan.application.CarePlanSchedules.Slot;
import sg.nus.carelink.careplan.application.CarePlanSchedules.Task;
import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.domain.model.CarePlanNode;
import sg.nus.carelink.careplan.domain.model.ScheduledVisit;

class CarePlanSchedulesServiceTest {

	private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
	private static final LocalDate OCT_8 = LocalDate.of(2026, 10, 8);
	private static final LocalTime EIGHT = LocalTime.of(8, 0);

	private final InMemoryCarePlanRepository plans = new InMemoryCarePlanRepository();
	private final InMemoryCarePlanNodeRepository nodes = new InMemoryCarePlanNodeRepository();
	private final CarePlanSchedulesService schedules = new CarePlanSchedulesService(plans, nodes);

	@Test
	void givesEachTaskWithItsWeeklyDays() {
		CarePlan plan = plans.save(CarePlan.startDraft(42L, 7L, null).publish(OCT_1, BigDecimal.ONE));
		CarePlanNode bathing = nodes.save(task(plan.id(), "Bathing assistance",
				new ScheduledVisit(DayOfWeek.MONDAY, EIGHT, 30), new ScheduledVisit(DayOfWeek.WEDNESDAY, EIGHT, 45)));

		assertThat(schedules.forElder(42L)).containsExactly(new PlanSchedule(plan.id(), 42L, 1, OCT_1, null,
				List.of(new Task(bathing.id(), "Personal care", "Bathing assistance", List.of(
						new Slot(DayOfWeek.MONDAY, EIGHT, 30), new Slot(DayOfWeek.WEDNESDAY, EIGHT, 45))))));
	}

	@Test
	void endsASupersededVersionWhereItsSuccessorStartsAndLeavesDraftsOut() {
		CarePlan first = plans.save(CarePlan.startDraft(42L, 7L, null).publish(OCT_1, BigDecimal.ONE));
		CarePlan second = plans.save(CarePlan.startDraft(42L, 7L, first).publish(OCT_8, BigDecimal.ONE));
		plans.save(first.supersede());
		plans.save(CarePlan.startDraft(42L, 7L, second));

		assertThat(schedules.forElder(42L))
				.extracting(PlanSchedule::carePlanId, PlanSchedule::effectiveFrom, PlanSchedule::effectiveUntil)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(first.id(), OCT_1, OCT_8),
						org.assertj.core.groups.Tuple.tuple(second.id(), OCT_8, null));
	}

	@Test
	void listsOnlyEldersWhosePlanHasLeftDraft() {
		plans.save(CarePlan.startDraft(42L, 7L, null).publish(OCT_1, BigDecimal.ONE));
		plans.save(CarePlan.startDraft(43L, 7L, null));

		assertThat(schedules.eldersWithSchedules()).containsExactly(42L);
	}

	private static CarePlanNode task(Long planId, String name, ScheduledVisit... visits) {
		return new CarePlanNode(null, planId, "Personal care", name, null, null, null,
				CarePlanNode.EvidenceType.NONE, 0, null, null, List.of(visits));
	}
}
