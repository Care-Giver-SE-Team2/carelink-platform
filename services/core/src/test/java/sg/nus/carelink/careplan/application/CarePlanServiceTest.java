package sg.nus.carelink.careplan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
	/** What publish asked the primary-caregiver clash check, and what it answers. */
	private final List<List<Object>> clashQuestions = new ArrayList<>();
	private Optional<String> clash = Optional.empty();
	private final CarePlanService service = new CarePlanService(repository, nodeRepository,
			(elderId, startDate, tasks) -> {
				clashQuestions.add(List.of(elderId, startDate, tasks));
				return clash;
			},
			events::add);

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
	void listsAnEldersVersionsNewestFirst() {
		CarePlan first = service.createDraft(42L, 7L);
		service.publish(first.id(), LocalDate.of(2026, 4, 1), List.of(new PlanNodeInput(
				null, "VITALS", "Vital-sign check", List.of(new VisitInput("Mon", EIGHT, 30)), CarePlanNode.EvidenceType.READING)));
		service.createDraft(42L, 7L);
		service.createDraft(43L, 7L);

		assertThat(service.findVersions(42L))
				.extracting(CarePlan::version, CarePlan::status)
				.containsExactly(
						tuple(2, CarePlan.Status.DRAFT),
						tuple(1, CarePlan.Status.PUBLISHED));
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
						"BATHING",
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
				"Personal care", "BATHING", "Bathing assistance",
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
				"Personal care", "BATHING", "Bathing assistance",
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
				new PlanNodeInput("Personal care", "BATHING", "Bathing assistance", List.of(), CarePlanNode.EvidenceType.CHECKLIST));

		assertThatThrownBy(() -> service.publish(draft.id(), LocalDate.of(2026, 4, 1), tasks))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_TASK_NO_VISITS");
	}

	@Test
	void savesADraftWithUnscheduledTasksAndNoStartDateYet() {
		CarePlan draft = service.createDraft(42L, 7L);

		CarePlan saved = service.saveDraft(draft.id(), null, List.of(
				new PlanNodeInput("Personal care", "BATHING", "Bathing assistance", List.of(), CarePlanNode.EvidenceType.CHECKLIST),
				new PlanNodeInput("Health monitoring", "VITALS", "Vital-sign check",
						List.of(new VisitInput("Mon", EIGHT, 15)), CarePlanNode.EvidenceType.READING)));

		assertThat(saved.status()).isEqualTo(CarePlan.Status.DRAFT);
		assertThat(service.findNodes(draft.id()))
				.extracting(CarePlanNode::activityCode, CarePlanNode::scheduleDays, CarePlanNode::visits)
				.containsExactly(
						tuple("BATHING", null, List.of()),
						tuple("VITALS", "MON", List.of(new ScheduledVisit(DayOfWeek.MONDAY, EIGHT, 15))));
		assertThat(events).isEmpty();
	}

	@Test
	void savingADraftAgainReplacesItsTasksAndKeepsTheStartDate() {
		CarePlan draft = service.createDraft(42L, 7L);
		service.saveDraft(draft.id(), null, List.of(
				new PlanNodeInput("Personal care", "BATHING", "Bathing assistance", List.of(), CarePlanNode.EvidenceType.CHECKLIST)));

		CarePlan saved = service.saveDraft(draft.id(), LocalDate.of(2026, 11, 2), List.of(
				new PlanNodeInput("Personal care", "GROOMING", "Grooming", List.of(), CarePlanNode.EvidenceType.CHECKLIST)));

		assertThat(saved.startDate()).isEqualTo(LocalDate.of(2026, 11, 2));
		assertThat(service.findNodes(draft.id())).extracting(CarePlanNode::name).containsExactly("Grooming");
	}

	@Test
	void anIssuedVersionCannotBeSavedAsADraftOrDiscarded() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<PlanNodeInput> tasks = List.of(new PlanNodeInput(
				"Personal care", "BATHING", "Bathing assistance",
				List.of(new VisitInput("Mon", EIGHT, 30)), CarePlanNode.EvidenceType.CHECKLIST));
		CarePlan published = service.publish(draft.id(), LocalDate.of(2026, 4, 1), tasks);

		assertThatThrownBy(() -> service.saveDraft(published.id(), null, tasks))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_NOT_DRAFT");
		assertThatThrownBy(() -> service.discardDraft(published.id()))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_NOT_DRAFT");
		assertThat(service.findNodes(published.id())).hasSize(1);
	}

	@Test
	void discardingADraftRemovesItAndLeavesThePublishedVersionInForce() {
		List<PlanNodeInput> tasks = List.of(new PlanNodeInput(
				"Personal care", "BATHING", "Bathing assistance",
				List.of(new VisitInput("Mon", EIGHT, 30)), CarePlanNode.EvidenceType.CHECKLIST));
		CarePlan published = service.publish(service.createDraft(42L, 7L).id(), LocalDate.of(2026, 4, 1), tasks);
		CarePlan draft = service.createDraft(42L, 7L);
		service.saveDraft(draft.id(), null, tasks);

		service.discardDraft(draft.id());

		assertThat(service.findCarePlan(draft.id())).isEmpty();
		assertThat(service.findNodes(draft.id())).isEmpty();
		assertThat(service.findLatestByElderId(42L)).contains(published);
	}

	@Test
	void aTaskNamingAnActivityOutsideTheCatalogIsRejected() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<PlanNodeInput> tasks = List.of(new PlanNodeInput(
				"Personal care", "SWIMMING", "Swimming", List.of(), CarePlanNode.EvidenceType.CHECKLIST));

		assertThatThrownBy(() -> service.saveDraft(draft.id(), null, tasks))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_UNKNOWN_ACTIVITY");
	}

	@Test
	void aTaskOutsideTheCatalogMayHaveNoActivityCode() {
		CarePlan draft = service.createDraft(42L, 7L);

		service.saveDraft(draft.id(), null, List.of(
				new PlanNodeInput(null, null, "Read the newspaper aloud", List.of(), CarePlanNode.EvidenceType.NONE)));

		assertThat(service.findNodes(draft.id()).getFirst().activityCode()).isNull();
	}

	@Test
	void stopsAPublishedPlanAndKeepsItsNodes() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<PlanNodeInput> tasks = List.of(new PlanNodeInput(
				"Personal care", "BATHING", "Bathing assistance",
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
				"Personal care", "BATHING", "Bathing assistance",
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
				"Personal care", "BATHING", "Bathing assistance",
				List.of(new VisitInput("Fri", LocalTime.of(8, 0), 60), new VisitInput("Mon", LocalTime.of(8, 0), 30),
						new VisitInput("Wed", LocalTime.of(16, 30), 90)),
				CarePlanNode.EvidenceType.CHECKLIST)));

		CarePlanNode task = service.findNodes(draft.id()).getFirst();
		assertThat(task.visits()).containsExactly(
				new ScheduledVisit(DayOfWeek.MONDAY, LocalTime.of(8, 0), 30),
				new ScheduledVisit(DayOfWeek.WEDNESDAY, LocalTime.of(16, 30), 90),
				new ScheduledVisit(DayOfWeek.FRIDAY, LocalTime.of(8, 0), 60));
		assertThat(task.weeklyHours()).isEqualByComparingTo("3.00");
		assertThat(task.scheduleDays()).isEqualTo("MON,WED,FRI");
	}

	@Test
	void publishingATaskThatSchedulesADayTwiceIsRejected() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<PlanNodeInput> tasks = List.of(new PlanNodeInput(
				"Personal care", "BATHING", "Bathing assistance",
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
				"Personal care", "BATHING", "Bathing assistance",
				List.of(new VisitInput("Mon", null, 30)), CarePlanNode.EvidenceType.CHECKLIST));

		assertThatThrownBy(() -> service.publish(draft.id(), LocalDate.of(2026, 4, 1), tasks))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_TASK_NO_START_TIME");
	}

	@Test
	void publishingAVisitThatStartsOffTheHalfHourIsRejected() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<PlanNodeInput> tasks = List.of(new PlanNodeInput(
				"Personal care", "BATHING", "Bathing assistance",
				List.of(new VisitInput("Mon", EIGHT, 30), new VisitInput("Wed", LocalTime.of(8, 15), 30)),
				CarePlanNode.EvidenceType.CHECKLIST));

		assertThatThrownBy(() -> service.publish(draft.id(), LocalDate.of(2026, 4, 1), tasks))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessage("Task [Bathing assistance] starts at 08:15 on Wed; visits start on the hour or half hour")
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_TASK_START_NOT_ON_HALF_HOUR");
		assertThat(service.findCarePlan(draft.id())).get().extracting(CarePlan::status)
				.isEqualTo(CarePlan.Status.DRAFT);
	}

	@Test
	void publishingAVisitOutsideThirtyMinuteBlocksUpToFourHoursIsRejected() {
		for (int minutes : new int[] {15, 45, 270}) {
			CarePlan draft = service.createDraft(100L + minutes, 7L);
			List<PlanNodeInput> tasks = List.of(new PlanNodeInput("Personal care", "BATHING", "Bathing assistance",
					List.of(new VisitInput("Mon", EIGHT, 30), new VisitInput("Tue", EIGHT, minutes)),
					CarePlanNode.EvidenceType.CHECKLIST));

			assertThatThrownBy(() -> service.publish(draft.id(), LocalDate.of(2026, 4, 1), tasks))
					.isInstanceOf(BusinessRuleViolation.class)
					.hasMessage("Task [Bathing assistance] runs %d minutes on Tue; visits run 30 minutes to 4 hours, "
							.formatted(minutes) + "in steps of 30 minutes")
					.extracting(ex -> ((BusinessRuleViolation) ex).code())
					.isEqualTo("CARE_PLAN_TASK_DURATION_NOT_ALLOWED");
		}
	}

	@Test
	void publishingAVisitThatRunsPastMidnightIsRejectedButOneEndingAtMidnightIsNot() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<PlanNodeInput> late = List.of(new PlanNodeInput("Medication support", null, "Night check",
				List.of(new VisitInput("Sat", LocalTime.of(23, 30), 60)), CarePlanNode.EvidenceType.CHECKLIST));

		assertThatThrownBy(() -> service.publish(draft.id(), LocalDate.of(2026, 4, 1), late))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessage("Task [Night check] starts at 23:30 on Sat and runs 60 minutes, past midnight; "
						+ "visits end by midnight")
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_TASK_PAST_MIDNIGHT");

		CarePlan published = service.publish(draft.id(), LocalDate.of(2026, 4, 1), List.of(new PlanNodeInput(
				"Medication support", null, "Night check",
				List.of(new VisitInput("Sat", LocalTime.of(23, 30), 30)), CarePlanNode.EvidenceType.CHECKLIST)));
		assertThat(published.status()).isEqualTo(CarePlan.Status.PUBLISHED);
	}

	@Test
	void asksWhetherThePrimaryCaregiverCanTakeTheNewVisitsAndRefusesWhenTheyClash() {
		CarePlan draft = service.createDraft(42L, 7L);
		clash = Optional.of("Aisyah N. is already primary caregiver of Chan Bee Choo, whose Bathing on Mon "
				+ "08:00–08:30 clashes with Meals on Mon 08:00–08:30");
		List<PlanNodeInput> tasks = List.of(new PlanNodeInput("Personal care", "MEAL_SUPPORT", "Meals",
				List.of(new VisitInput("Mon", EIGHT, 30)), CarePlanNode.EvidenceType.CHECKLIST));

		assertThatThrownBy(() -> service.publish(draft.id(), LocalDate.of(2026, 4, 1), tasks))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessage(clash.get())
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CAREGIVER_TIME_CLASH");

		assertThat(clashQuestions).singleElement().satisfies(question -> {
			assertThat(question.get(0)).isEqualTo(42L);
			assertThat(question.get(1)).isEqualTo(LocalDate.of(2026, 4, 1));
			assertThat((List<?>) question.get(2)).singleElement().isInstanceOfSatisfying(CarePlanSchedules.Task.class,
					task -> {
						assertThat(task.name()).isEqualTo("Meals");
						assertThat(task.slots()).containsExactly(new CarePlanSchedules.Slot(DayOfWeek.MONDAY, EIGHT, 30));
					});
		});
		assertThat(service.findCarePlan(draft.id())).get().extracting(CarePlan::status)
				.isEqualTo(CarePlan.Status.DRAFT);
		assertThat(events).isEmpty();
	}

	@Test
	void publishingTwoTasksThatOverlapOnTheSameDayIsRejected() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<PlanNodeInput> tasks = List.of(
				new PlanNodeInput("Personal care", "BATHING", "Bathing assistance",
						List.of(new VisitInput("Mon", EIGHT, 60)), CarePlanNode.EvidenceType.CHECKLIST),
				new PlanNodeInput("Personal care", "GROOMING", "Grooming",
						List.of(new VisitInput("Tue", EIGHT, 30), new VisitInput("Mon", LocalTime.of(8, 30), 30)),
						CarePlanNode.EvidenceType.CHECKLIST));

		assertThatThrownBy(() -> service.publish(draft.id(), LocalDate.of(2026, 4, 1), tasks))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessage("Tasks [Bathing assistance] (08:00–09:00) and [Grooming] (08:30–09:00) overlap on Mon; "
						+ "one caregiver can't do both, so stagger them or make them one task")
				.extracting(ex -> ((BusinessRuleViolation) ex).code())
				.isEqualTo("CARE_PLAN_TASKS_OVERLAP");
		assertThat(service.findCarePlan(draft.id())).get().extracting(CarePlan::status)
				.isEqualTo(CarePlan.Status.DRAFT);
	}

	@Test
	void tasksBackToBackOrOnOtherDaysPublish() {
		CarePlan draft = service.createDraft(42L, 7L);

		CarePlan published = service.publish(draft.id(), LocalDate.of(2026, 4, 1), List.of(
				new PlanNodeInput("Personal care", "BATHING", "Bathing assistance",
						List.of(new VisitInput("Mon", EIGHT, 60)), CarePlanNode.EvidenceType.CHECKLIST),
				new PlanNodeInput("Personal care", "GROOMING", "Grooming",
						List.of(new VisitInput("Mon", LocalTime.of(9, 0), 30), new VisitInput("Tue", EIGHT, 30)),
						CarePlanNode.EvidenceType.CHECKLIST)));

		assertThat(published.status()).isEqualTo(CarePlan.Status.PUBLISHED);
	}

	@Test
	void publishesAFourHourVisit() {
		CarePlan draft = service.createDraft(42L, 7L);

		CarePlan published = service.publish(draft.id(), LocalDate.of(2026, 4, 1), List.of(new PlanNodeInput(
				"Social and mobility", "ERRAND_ACCOMPANIMENT", "Errand accompaniment",
				List.of(new VisitInput("Sat", LocalTime.of(9, 0), 240)), CarePlanNode.EvidenceType.CHECKLIST)));

		assertThat(published.totalHours()).isEqualByComparingTo("4.00");
	}

	@Test
	void aDraftMayStillHoldATimeOffTheHalfHourAndPublishesOnceItIsMoved() {
		CarePlan draft = service.createDraft(42L, 7L);
		service.saveDraft(draft.id(), null, List.of(new PlanNodeInput("Personal care", "BATHING", "Bathing assistance",
				List.of(new VisitInput("Mon", LocalTime.of(8, 15), 30)), CarePlanNode.EvidenceType.CHECKLIST)));

		CarePlan published = service.publish(draft.id(), LocalDate.of(2026, 4, 1), List.of(new PlanNodeInput(
				"Personal care", "BATHING", "Bathing assistance",
				List.of(new VisitInput("Mon", LocalTime.of(8, 30), 30)), CarePlanNode.EvidenceType.CHECKLIST)));

		assertThat(published.status()).isEqualTo(CarePlan.Status.PUBLISHED);
	}

	@Test
	void schedulesSevenVisitsAWeekAsDaily() {
		CarePlan draft = service.createDraft(42L, 7L);
		List<VisitInput> everyDay = List.of(
				new VisitInput("Mon", EIGHT, 30), new VisitInput("Tue", EIGHT, 30), new VisitInput("Wed", EIGHT, 30),
				new VisitInput("Thu", EIGHT, 30), new VisitInput("Fri", EIGHT, 30), new VisitInput("Sat", EIGHT, 30),
				new VisitInput("Sun", EIGHT, 30));

		service.publish(draft.id(), LocalDate.of(2026, 4, 1), List.of(
				new PlanNodeInput("Personal care", "BATHING", "Bathing assistance", everyDay, CarePlanNode.EvidenceType.CHECKLIST)));

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
				"Personal care", "BATHING", "Bathing assistance",
				List.of(new VisitInput("Mon", EIGHT, 30)), CarePlanNode.EvidenceType.CHECKLIST)));
		service.stop(published.id(), LocalDate.of(2026, 9, 22), "Elder moved away", 9L);

		assertThat(events).containsExactly(
				new CarePlanScheduleChanged(42L),
				new CarePlanPublished(42L, published.id(), 1, LocalDate.of(2026, 4, 1)),
				new CarePlanScheduleChanged(42L));
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
