package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

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
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.testsupport.VisitStandIn;
import sg.nus.carelink.testsupport.VisitStandIn.Held;

/**
 * UC-MG03 end to end on real MySQL: publishing a plan fills the elder's next two weeks with
 * visits once the publish commits, naming a primary caregiver covers them, and stopping the
 * plan calls off the ones from the stop date. The visits are visit's, here its stand-in's.
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
	@Autowired
	private VisitStandIn visits;

	@Test
	void publishingNamingACaregiverAndStoppingKeepTheVisitsInStep() {
		Long elderId = elder();
		LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
		CarePlan draft = carePlans.createDraft(elderId, null);

		carePlans.publish(draft.id(), tomorrow, List.of(new PlanNodeInput("Personal care", "BATHING", "Bathing assistance",
				Arrays.stream(DayOfWeek.values()).map(day -> new VisitInput(day.name(), LocalTime.of(8, 0), 30)).toList(),
				CarePlanNode.EvidenceType.CHECKLIST)));

		// Tomorrow through the last day of the 14-day window, one visit a day, nobody named yet.
		assertThat(count(elderId, visit -> visit.status().equals("SCHEDULED") && visit.caregiverId() == null))
				.isEqualTo(13);
		assertThat(count(elderId, visit -> draft.id().equals(visit.carePlanId()) && visit.carePlanNodeId() != null))
				.isEqualTo(13);

		Long caregiverId = caregiver();
		primaryCaregivers.assign(elderId, caregiverId);

		assertThat(count(elderId, visit -> visit.status().equals("SCHEDULED") && caregiverId.equals(visit.caregiverId())))
				.isEqualTo(13);

		carePlans.stop(draft.id(), tomorrow.plusDays(6), "Moving in with family", null);

		assertThat(count(elderId, visit -> visit.status().equals("SCHEDULED"))).isEqualTo(6);
		assertThat(count(elderId, visit -> visit.status().equals("CANCELLED"))).isEqualTo(7);

		assertThat(roster.refreshElder(elderId)).isEqualTo(new RecurringRosterService.RosterRefresh(elderId, 0, 0, 0, 0));
	}

	@Test
	void replacingThePrimaryCaregiverHandsTheirUntouchedVisitsOverAndRemovingThemLeavesVisitsToBeCovered() {
		Long elderId = elder();
		Long first = caregiver();
		Long second = caregiver();
		primaryCaregivers.assign(elderId, first);
		CarePlan plan = publishDaily(elderId);
		assertThat(count(elderId, visit -> first.equals(visit.caregiverId()))).isEqualTo(13);
		Long extraService = visits.add(Held.scheduled(elderId, first, LocalDateTime.now(clock).plusDays(2),
				LocalDateTime.now(clock).plusDays(2).plusHours(1)).withService("Escort to clinic"));

		primaryCaregivers.assign(elderId, second);

		assertThat(count(elderId, visit -> plan.id().equals(visit.carePlanId()) && second.equals(visit.caregiverId())))
				.isEqualTo(13);
		assertThat(visits.current(extraService).caregiverId()).isEqualTo(first);

		primaryCaregivers.unassign(elderId);

		assertThat(count(elderId, visit -> plan.id().equals(visit.carePlanId()) && visit.caregiverId() == null))
				.isEqualTo(13);
	}

	@Test
	void refusesAPrimaryCaregiverWhoseOtherElderIsVisitedAtTheSameTime() {
		Long held = elder();
		Long wanted = elder();
		publishDaily(held);
		publishDaily(wanted);
		Long caregiverId = caregiver();
		primaryCaregivers.assign(held, caregiverId);

		assertThatThrownBy(() -> primaryCaregivers.assign(wanted, caregiverId))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(e -> ((BusinessRuleViolation) e).code()).isEqualTo("CAREGIVER_TIME_CLASH");
		assertThat(count(wanted, visit -> visit.caregiverId() != null)).isZero();
	}

	@Test
	void refusesToPublishAVersionThatClashesWithAnotherElderOfThePrimaryCaregiverAndKeepsNothing() {
		Long held = elder();
		Long elderId = elder();
		publishDaily(held);
		CarePlan current = publishDailyAt(elderId, LocalTime.of(10, 0));
		Long caregiverId = caregiver();
		primaryCaregivers.assign(held, caregiverId);
		primaryCaregivers.assign(elderId, caregiverId);
		CarePlan draft = carePlans.createDraft(elderId, null);

		assertThatThrownBy(() -> carePlans.publish(draft.id(), LocalDate.now(clock).plusDays(3),
				dailyAt(LocalTime.of(8, 0))))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(e -> ((BusinessRuleViolation) e).code()).isEqualTo("CAREGIVER_TIME_CLASH");

		assertThat(carePlans.findCarePlan(draft.id())).get().extracting(CarePlan::status)
				.isEqualTo(CarePlan.Status.DRAFT);
		assertThat(carePlans.findNodes(draft.id())).isEmpty();
		assertThat(carePlans.findCarePlan(current.id())).get().extracting(CarePlan::status)
				.isEqualTo(CarePlan.Status.PUBLISHED);
	}

	@Test
	void aPrimaryCaregiverWhoGoesInactiveHasTheirUntouchedVisitsReleasedOnTheNextRefresh() {
		Long elderId = elder();
		Long caregiverId = caregiver();
		primaryCaregivers.assign(elderId, caregiverId);
		CarePlan plan = publishDaily(elderId);
		assertThat(count(elderId, visit -> caregiverId.equals(visit.caregiverId()))).isEqualTo(13);

		// Nothing in the app sets it yet; an admin or a later feature might, however it happens.
		jdbc.update("UPDATE caregiver SET status = 'INACTIVE' WHERE id = ?", caregiverId);

		assertThat(roster.refreshElder(elderId).released()).isEqualTo(13);
		assertThat(count(elderId, visit -> plan.id().equals(visit.carePlanId()) && visit.caregiverId() == null))
				.isEqualTo(13);
		assertThat(roster.refreshElder(elderId).released()).isZero();
	}

	@Test
	void refusesAPrimaryCaregiverWithoutACertificateThePlanRequires() {
		Long elderId = elder();
		CarePlan plan = publishDaily(elderId);
		jdbc.update("INSERT INTO credential_type (name) VALUES (?)", "Roster test certificate " + plan.id());
		Long typeId = jdbc.queryForObject("SELECT MAX(id) FROM credential_type", Long.class);
		jdbc.update("INSERT INTO care_plan_required_credential (care_plan_id, credential_type_id) VALUES (?, ?)",
				plan.id(), typeId);
		Long caregiverId = caregiver();

		assertThatThrownBy(() -> primaryCaregivers.assign(elderId, caregiverId))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(e -> ((BusinessRuleViolation) e).code()).isEqualTo("CAREGIVER_MISSING_CREDENTIAL");
		assertThat(count(elderId, visit -> visit.caregiverId() != null)).isZero();
	}

	@Test
	void aVisitStillUnassignedAtItsStartBecomesAnExceptionWithAnIncident() {
		Long elderId = elder();
		Long visitId = visits.add(Held.scheduled(elderId, null, LocalDateTime.now(clock).minusMinutes(5),
				LocalDateTime.now(clock).plusMinutes(55)).withService("Companionship"));

		uncovered.startedUncovered().stream()
				.filter(visit -> visit.visitId().equals(visitId))
				.forEach(uncovered::escalate);

		assertThat(visits.current(visitId).status()).isEqualTo("EXCEPTION");
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM incident WHERE visit_id = ? AND source = 'SYSTEM_MISSED_CHECKIN'",
				Integer.class, visitId)).isEqualTo(1);
	}

	/** A plan from tomorrow with one 08:00 visit every day: 13 visits in the 14-day window. */
	private CarePlan publishDaily(Long elderId) {
		return publishDailyAt(elderId, LocalTime.of(8, 0));
	}

	private CarePlan publishDailyAt(Long elderId, LocalTime start) {
		CarePlan draft = carePlans.createDraft(elderId, null);
		return carePlans.publish(draft.id(), LocalDate.now(clock).plusDays(1), dailyAt(start));
	}

	private static List<PlanNodeInput> dailyAt(LocalTime start) {
		return List.of(new PlanNodeInput("Personal care", "BATHING", "Bathing assistance",
				Arrays.stream(DayOfWeek.values()).map(day -> new VisitInput(day.name(), start, 30)).toList(),
				CarePlanNode.EvidenceType.CHECKLIST));
	}

	private long count(Long elderId, Predicate<Held> condition) {
		return visits.ofElder(elderId).stream().filter(condition).count();
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
