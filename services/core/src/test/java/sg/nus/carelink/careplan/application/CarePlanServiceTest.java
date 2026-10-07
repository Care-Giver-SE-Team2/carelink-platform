package sg.nus.carelink.careplan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.domain.model.CarePlanNode;
import sg.nus.carelink.careplan.domain.model.ScheduledVisit;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

class CarePlanServiceTest {

	private static final LocalTime EIGHT = LocalTime.of(8, 0);

	private final InMemoryCarePlanRepository repository = new InMemoryCarePlanRepository();
	private final InMemoryCarePlanNodeRepository nodeRepository = new InMemoryCarePlanNodeRepository();
	private final List<Object> events = new ArrayList<>();
	private final CarePlanService service = new CarePlanService(repository, nodeRepository, events::add);

	@Test
	void findsWhatWasSaved() {
		CarePlan saved = repository.save(new CarePlan(
				null,
				2L,
				3L,
				4L,
				5,
				CarePlan.Status.DRAFT,
				new BigDecimal("7.5"),
				LocalDateTime.of(2026, 9, 6, 10, 8),
				LocalDateTime.of(2026, 9, 6, 10, 9),
				LocalDateTime.of(2026, 9, 6, 10, 10)));

		assertThat(service.findCarePlan(saved.id())).contains(saved);
	}

	@Test
	void isEmptyForAnUnknownId() {
		assertThat(service.findCarePlan(999L)).isEmpty();
	}

	@Test
	void createsAnEmptyDraftForAnElderWithNoPriorPlan() {
		CarePlan created = service.createDraft(42L, 7L);

		assertThat(created.id()).isNotNull();
		assertThat(created.elderId()).isEqualTo(42L);
		assertThat(created.createdByUserId()).isEqualTo(7L);
		assertThat(created.version()).isEqualTo(1);
		assertThat(created.status()).isEqualTo(CarePlan.Status.DRAFT);
	}

	@Test
	void publishesADraftAndRollsUpTotalHoursFromItsTasks() {
		CarePlan draft = service.createDraft(42L, 7L);

		CarePlan published = service.publish(draft.id(), LocalDate.of(2026, 4, 1), List.of(
				new PlanNodeInput(
						"Personal care",
						"Bathing assistance",
						List.of(new VisitInput("Mon", EIGHT, 30), new VisitInput("Wed", EIGHT, 30), new VisitInput("Fri", EIGHT, 30)),
						CarePlanNode.EvidenceType.CHECKLIST)));

		assertThat(published.status()).isEqualTo(CarePlan.Status.PUBLISHED);
		assertThat(published.publishedAt()).isNotNull();
		assertThat(published.startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
		assertThat(published.totalHours()).isEqualByComparingTo("1.50");

		List<CarePlanNode> nodes = service.findNodes(draft.id());
		assertThat(nodes).hasSize(1);
	}

	@Test
	void publishingAnAlreadyPublishedPlanIsRejected() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<PlanNodeInput> tasks = List.of(new PlanNodeInput(
				"Personal care", "Bathing assistance",
				List.of(new VisitInput("Mon", EIGHT, 30)), CarePlanNode.EvidenceType.CHECKLIST));
		CarePlan published = service.publish(draft.id(), LocalDate.of(2026, 4, 1), tasks);

		assertThatThrownBy(() -> service.publish(published.id(), LocalDate.of(2026, 4, 1), tasks))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_NOT_DRAFT");
	}

	@Test
	void publishingWithoutAStartDateIsRejected() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<PlanNodeInput> tasks = List.of(new PlanNodeInput(
				"Personal care", "Bathing assistance",
				List.of(new VisitInput("Mon", EIGHT, 30)), CarePlanNode.EvidenceType.CHECKLIST));

		assertThatThrownBy(() -> service.publish(draft.id(), null, tasks))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_START_DATE_REQUIRED");
	}

	@Test
	void publishingATaskWithNoVisitsIsRejected() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<PlanNodeInput> tasks = List.of(
				new PlanNodeInput("Personal care", "Bathing assistance", List.of(), CarePlanNode.EvidenceType.CHECKLIST));

		assertThatThrownBy(() -> service.publish(draft.id(), LocalDate.of(2026, 4, 1), tasks))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_TASK_NO_VISITS");
	}

	@Test
	void stopsAPublishedPlanAndKeepsItsNodes() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<PlanNodeInput> tasks = List.of(new PlanNodeInput(
				"Personal care", "Bathing assistance",
				List.of(new VisitInput("Mon", EIGHT, 30)), CarePlanNode.EvidenceType.CHECKLIST));
		CarePlan published = service.publish(draft.id(), LocalDate.of(2026, 4, 1), tasks);

		CarePlan stopped = service.stop(published.id(), LocalDate.of(2026, 9, 22), "Elder moved away", 9L);

		assertThat(stopped.status()).isEqualTo(CarePlan.Status.STOPPED);
		assertThat(stopped.stopEffectiveDate()).isEqualTo(LocalDate.of(2026, 9, 22));
		assertThat(stopped.stopReason()).isEqualTo("Elder moved away");
		assertThat(stopped.stoppedByUserId()).isEqualTo(9L);
		assertThat(service.findNodes(published.id())).hasSize(1);
	}

	@Test
	void findsTheElderSLatestPlanByVersion() {
		CarePlan draft = service.createDraft(42L, 7L);

		assertThat(service.findLatestByElderId(42L)).contains(draft);
	}

	@Test
	void isEmptyWhenTheElderHasNoPlanYet() {
		assertThat(service.findLatestByElderId(999L)).isEmpty();
	}

	@Test
	void publishingANewDraftSupersedesThePreviousPublishedPlan() {
		List<PlanNodeInput> tasks = List.of(new PlanNodeInput(
				"Personal care", "Bathing assistance",
				List.of(new VisitInput("Mon", EIGHT, 30)), CarePlanNode.EvidenceType.CHECKLIST));
		CarePlan firstDraft = service.createDraft(42L, 7L);
		CarePlan firstPublished = service.publish(firstDraft.id(), LocalDate.of(2026, 4, 1), tasks);

		CarePlan secondDraft = service.createDraft(42L, 7L);
		service.publish(secondDraft.id(), LocalDate.of(2026, 4, 1), tasks);

		assertThat(service.findCarePlan(firstPublished.id()).orElseThrow().status())
				.isEqualTo(CarePlan.Status.SUPERSEDED);
	}

	@Test
	void keepsEachDaysStartTimeAndMinutesAndSumsThemIntoWeeklyHours() {
		CarePlan draft = service.createDraft(42L, 7L);

		service.publish(draft.id(), LocalDate.of(2026, 4, 1), List.of(new PlanNodeInput(
				"Personal care", "Bathing assistance",
				List.of(new VisitInput("Fri", LocalTime.of(8, 0), 60), new VisitInput("Mon", LocalTime.of(8, 0), 30),
						new VisitInput("Wed", LocalTime.of(16, 30), 45)),
				CarePlanNode.EvidenceType.CHECKLIST)));

		CarePlanNode task = service.findNodes(draft.id()).getFirst();
		assertThat(task.visits()).containsExactly(
				new ScheduledVisit(DayOfWeek.MONDAY, LocalTime.of(8, 0), 30),
				new ScheduledVisit(DayOfWeek.WEDNESDAY, LocalTime.of(16, 30), 45),
				new ScheduledVisit(DayOfWeek.FRIDAY, LocalTime.of(8, 0), 60));
		assertThat(task.weeklyHours()).isEqualByComparingTo("2.25");
		assertThat(task.scheduleDays()).isEqualTo("MON,WED,FRI");
	}

	@Test
	void publishingATaskThatSchedulesADayTwiceIsRejected() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<PlanNodeInput> tasks = List.of(new PlanNodeInput(
				"Personal care", "Bathing assistance",
				List.of(new VisitInput("Mon", EIGHT, 30), new VisitInput("Monday", EIGHT, 15)),
				CarePlanNode.EvidenceType.CHECKLIST));

		assertThatThrownBy(() -> service.publish(draft.id(), LocalDate.of(2026, 4, 1), tasks))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_TASK_DUPLICATE_DAY");
	}

	@Test
	void publishingADayWithNoStartTimeIsRejected() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<PlanNodeInput> tasks = List.of(new PlanNodeInput(
				"Personal care", "Bathing assistance",
				List.of(new VisitInput("Mon", null, 30)), CarePlanNode.EvidenceType.CHECKLIST));

		assertThatThrownBy(() -> service.publish(draft.id(), LocalDate.of(2026, 4, 1), tasks))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_TASK_NO_START_TIME");
	}

	@Test
	void schedulesSevenVisitsAWeekAsDaily() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<VisitInput> everyDay = List.of(
				new VisitInput("Mon", EIGHT, 30), new VisitInput("Tue", EIGHT, 30), new VisitInput("Wed", EIGHT, 30),
				new VisitInput("Thu", EIGHT, 30), new VisitInput("Fri", EIGHT, 30), new VisitInput("Sat", EIGHT, 30),
				new VisitInput("Sun", EIGHT, 30));

		service.publish(draft.id(), LocalDate.of(2026, 4, 1), List.of(
				new PlanNodeInput("Personal care", "Bathing assistance", everyDay, CarePlanNode.EvidenceType.CHECKLIST)));

		assertThat(service.findNodes(draft.id()).getFirst().scheduleDays()).isEqualTo("DAILY");
	}

	@Test
	void stoppingADraftIsRejected() {
		CarePlan draft = service.createDraft(42L, 7L);

		assertThatThrownBy(() -> service.stop(draft.id(), LocalDate.of(2026, 9, 22), "reason", 9L))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_NOT_PUBLISHED");
	}

	@Test
	void announcesTheScheduleChangeWhenAPlanIsPublishedAndWhenItIsStopped() {
		CarePlan draft = service.createDraft(42L, 7L);
		CarePlan published = service.publish(draft.id(), LocalDate.of(2026, 4, 1), List.of(new PlanNodeInput(
				"Personal care", "Bathing assistance",
				List.of(new VisitInput("Mon", EIGHT, 30)), CarePlanNode.EvidenceType.CHECKLIST)));
		service.stop(published.id(), LocalDate.of(2026, 9, 22), "Elder moved away", 9L);

		assertThat(events).containsExactly(new CarePlanScheduleChanged(42L), new CarePlanScheduleChanged(42L));
	}

	@Test
	void announcesNothingWhenAPublishIsRejected() {
		CarePlan draft = service.createDraft(42L, 7L);

		assertThatThrownBy(() -> service.publish(draft.id(), null, List.of()))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_START_DATE_REQUIRED");
		assertThat(events).isEmpty();
	}
}
