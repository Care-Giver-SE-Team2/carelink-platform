package sg.nus.carelink.careplan.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.domain.model.CarePlanNode;
import sg.nus.carelink.careplan.domain.model.ScheduledVisit;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * A task's per-day schedule survives a publish on real MySQL: each day's start time and minutes
 * land in care_plan_node_visit and come back as entered, and each version keeps its own schedule.
 */
@SpringBootTest
class CarePlanScheduleIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, CarePlanScheduleIT.class, null);
	}

	@Autowired
	private CarePlanService service;
	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void keepsEachDaysStartTimeAndMinutesThroughAPublish() {
		Long elderId = elder();
		CarePlan draft = service.createDraft(elderId, null);

		service.publish(draft.id(), LocalDate.of(2026, 10, 1), List.of(new PlanNodeInput(
				"Personal care", "Bathing assistance",
				List.of(new VisitInput("Mon", LocalTime.of(8, 0), 30), new VisitInput("Wed", LocalTime.of(16, 30), 45)),
				CarePlanNode.EvidenceType.CHECKLIST)));

		CarePlanNode task = service.findNodes(draft.id()).getFirst();
		assertThat(task.visits()).containsExactly(
				new ScheduledVisit(DayOfWeek.MONDAY, LocalTime.of(8, 0), 30),
				new ScheduledVisit(DayOfWeek.WEDNESDAY, LocalTime.of(16, 30), 45));
		assertThat(task.weeklyHours()).isEqualByComparingTo("1.25");
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM care_plan_node_visit WHERE care_plan_node_id = ?", Integer.class, task.id()))
				.isEqualTo(2);
	}

	@Test
	void theNextVersionHasItsOwnScheduleAndLeavesThePreviousOneAlone() {
		Long elderId = elder();
		CarePlan first = service.createDraft(elderId, null);
		service.publish(first.id(), LocalDate.of(2026, 10, 1), List.of(new PlanNodeInput(
				null, "Vital-sign check", List.of(new VisitInput("Tue", LocalTime.of(9, 0), 15)),
				CarePlanNode.EvidenceType.READING)));

		CarePlan second = service.createDraft(elderId, null);
		service.publish(second.id(), LocalDate.of(2026, 10, 8), List.of(new PlanNodeInput(
				null, "Vital-sign check", List.of(new VisitInput("Thu", LocalTime.of(10, 0), 20)),
				CarePlanNode.EvidenceType.READING)));

		assertThat(service.findNodes(first.id()).getFirst().visits())
				.containsExactly(new ScheduledVisit(DayOfWeek.TUESDAY, LocalTime.of(9, 0), 15));
		assertThat(service.findNodes(second.id()).getFirst().visits())
				.containsExactly(new ScheduledVisit(DayOfWeek.THURSDAY, LocalTime.of(10, 0), 20));
	}

	private Long elder() {
		jdbc.update("INSERT INTO elder (full_name) VALUES ('Schedule test elder')");
		return jdbc.queryForObject("SELECT MAX(id) FROM elder", Long.class);
	}
}
