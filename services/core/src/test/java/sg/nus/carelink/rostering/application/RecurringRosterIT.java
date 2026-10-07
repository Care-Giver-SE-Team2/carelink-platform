package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import sg.nus.carelink.careplan.application.CarePlanService;
import sg.nus.carelink.careplan.application.PlanNodeInput;
import sg.nus.carelink.careplan.application.VisitInput;
import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.domain.model.CarePlanNode;
import sg.nus.carelink.profile.application.PrimaryCaregiverService;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * UC-MG03 end to end on real MySQL: publishing a plan fills the elder's next two weeks with
 * visits once the publish commits, naming a primary caregiver covers them, and stopping the
 * plan calls off the ones from the stop date.
 */
@SpringBootTest
class RecurringRosterIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, RecurringRosterIT.class, null);
	}

	@Autowired
	private CarePlanService carePlans;
	@Autowired
	private PrimaryCaregiverService primaryCaregivers;
	@Autowired
	private RecurringRosterService roster;
	@Autowired
	private UncoveredVisitService uncovered;
	@Autowired
	private Clock clock;
	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void publishingNamingACaregiverAndStoppingKeepTheVisitsInStep() {
		Long elderId = elder();
		LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
		CarePlan draft = carePlans.createDraft(elderId, null);

		carePlans.publish(draft.id(), tomorrow, List.of(new PlanNodeInput("Personal care", "Bathing assistance",
				Arrays.stream(DayOfWeek.values()).map(day -> new VisitInput(day.name(), LocalTime.of(8, 0), 30)).toList(),
				CarePlanNode.EvidenceType.CHECKLIST)));

		// Tomorrow through the last day of the 14-day window, one visit a day, nobody named yet.
		assertThat(count(elderId, "status = 'SCHEDULED' AND caregiver_id IS NULL")).isEqualTo(13);
		assertThat(count(elderId, "care_plan_id = " + draft.id() + " AND care_plan_node_id IS NOT NULL")).isEqualTo(13);

		Long caregiverId = caregiver();
		primaryCaregivers.assign(elderId, caregiverId);

		assertThat(count(elderId, "status = 'SCHEDULED' AND caregiver_id = " + caregiverId)).isEqualTo(13);

		carePlans.stop(draft.id(), tomorrow.plusDays(6), "Moving in with family", null);

		assertThat(count(elderId, "status = 'SCHEDULED'")).isEqualTo(6);
		assertThat(count(elderId, "status = 'CANCELLED'")).isEqualTo(7);

		assertThat(roster.refreshElder(elderId)).isEqualTo(new RecurringRosterService.RosterRefresh(elderId, 0, 0, 0));
	}

	@Test
	void aVisitStillUnassignedAtItsStartBecomesAnExceptionWithAnIncident() {
		Long elderId = elder();
		jdbc.update("INSERT INTO visit (elder_id, service_type, scheduled_start, scheduled_end, status) VALUES (?, 'Companionship', ?, ?, 'SCHEDULED')",
				elderId, LocalDateTime.now(clock).minusMinutes(5), LocalDateTime.now(clock).plusMinutes(55));
		Long visitId = jdbc.queryForObject("SELECT MAX(id) FROM visit", Long.class);

		uncovered.startedUncovered().stream()
				.filter(visit -> visit.visitId().equals(visitId))
				.forEach(uncovered::escalate);

		assertThat(count(elderId, "status = 'EXCEPTION'")).isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM incident WHERE visit_id = ? AND source = 'SYSTEM_MISSED_CHECKIN'",
				Integer.class, visitId)).isEqualTo(1);
	}

	private int count(Long elderId, String condition) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE elder_id = ? AND " + condition,
				Integer.class, elderId);
	}

	private Long elder() {
		jdbc.update("INSERT INTO elder (full_name) VALUES ('Roster test elder')");
		return jdbc.queryForObject("SELECT MAX(id) FROM elder", Long.class);
	}

	private Long caregiver() {
		jdbc.update("INSERT INTO caregiver (user_id, full_name, status) "
				+ "SELECT COALESCE(MAX(user_id), 0) + 1000, 'Roster test caregiver', 'AVAILABLE' FROM caregiver");
		return jdbc.queryForObject("SELECT MAX(id) FROM caregiver", Long.class);
	}
}
