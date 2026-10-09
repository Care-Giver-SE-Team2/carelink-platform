package sg.nus.carelink.careplan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.careplan.domain.model.CarePlan;
import sg.nus.carelink.careplan.domain.model.CarePlanNode;

/**
 * findLatestByElderId just delegates to the repository port; findNextVisitDate additionally
 * derives a date from the plan's start date and its nodes' weekly schedule.
 */
class CarePlanLookupServiceTest {

	private final InMemoryCarePlanRepository repository = new InMemoryCarePlanRepository();
	private final InMemoryCarePlanNodeRepository nodeRepository = new InMemoryCarePlanNodeRepository();
	private final CarePlanLookup lookup = new CarePlanLookupService(repository, nodeRepository);

	@Test
	void findsTheElderSLatestPlan() {
		CarePlan saved = repository.save(new CarePlan(
				null, 42L, 7L, null, 1, CarePlan.Status.DRAFT, new BigDecimal("7.5"),
				null, LocalDateTime.of(2026, 9, 6, 10, 8), LocalDateTime.of(2026, 9, 6, 10, 9)));

		assertThat(lookup.findLatestByElderId(42L)).contains(saved);
	}

	@Test
	void isEmptyWhenTheElderHasNoPlanYet() {
		assertThat(lookup.findLatestByElderId(999L)).isEmpty();
	}

	@Test
	void nextVisitDateIsTheFirstScheduledDayOnOrAfterTheStartDate() {
		CarePlan plan = publishedPlan(1L, 42L, LocalDate.of(2026, 9, 23)); // a Wednesday
		repository.save(plan);
		nodeRepository.save(node(plan.id(), "WED,FRI"));

		assertThat(lookup.findNextVisitDate(42L, LocalDate.of(2026, 9, 20)))
				.contains(LocalDate.of(2026, 9, 23));
	}

	@Test
	void nextVisitDateSkipsAheadToTheNextScheduledWeekday() {
		CarePlan plan = publishedPlan(2L, 43L, LocalDate.of(2026, 9, 1));
		repository.save(plan);
		nodeRepository.save(node(plan.id(), "FRI"));

		assertThat(lookup.findNextVisitDate(43L, LocalDate.of(2026, 9, 23))) // a Wednesday
				.contains(LocalDate.of(2026, 9, 25));
	}

	@Test
	void nextVisitDateIsEmptyWithoutAPublishedPlan() {
		CarePlan draft = new CarePlan(
				3L, 44L, 7L, null, 1, CarePlan.Status.DRAFT, new BigDecimal("7.5"),
				null, LocalDateTime.of(2026, 9, 6, 10, 8), LocalDateTime.of(2026, 9, 6, 10, 9));
		repository.save(draft);

		assertThat(lookup.findNextVisitDate(44L, LocalDate.of(2026, 9, 23))).isEmpty();
	}

	@Test
	void nextVisitDateIsEmptyWhenNoNodeHasASchedule() {
		CarePlan plan = publishedPlan(5L, 45L, LocalDate.of(2026, 9, 1));
		repository.save(plan);

		assertThat(lookup.findNextVisitDate(45L, LocalDate.of(2026, 9, 23))).isEmpty();
	}

	@Test
	void nextVisitDateIsEmptyWhenTheElderHasNoPlanAtAll() {
		assertThat(lookup.findNextVisitDate(999L, LocalDate.of(2026, 9, 23))).isEmpty();
	}

	@Test
	void nextVisitDateIsEmptyWhenThePublishedPlanHasNoStartDateYet() {
		CarePlan plan = publishedPlan(6L, 46L, null);
		repository.save(plan);
		nodeRepository.save(node(plan.id(), "WED"));

		assertThat(lookup.findNextVisitDate(46L, LocalDate.of(2026, 9, 23))).isEmpty();
	}

	@Test
	void nextVisitDateIgnoresNodesWithoutAScheduleWhenAnotherNodeHasOne() {
		CarePlan plan = publishedPlan(7L, 47L, LocalDate.of(2026, 9, 1));
		repository.save(plan);
		nodeRepository.save(node(plan.id(), ""));
		nodeRepository.save(node(plan.id(), "FRI"));

		assertThat(lookup.findNextVisitDate(47L, LocalDate.of(2026, 9, 23))) // a Wednesday
				.contains(LocalDate.of(2026, 9, 25));
	}

	@Test
	void nextVisitDateTreatsDailyAsEveryDayOfTheWeek() {
		CarePlan plan = publishedPlan(8L, 48L, LocalDate.of(2026, 9, 1));
		repository.save(plan);
		nodeRepository.save(node(plan.id(), "DAILY"));

		assertThat(lookup.findNextVisitDate(48L, LocalDate.of(2026, 9, 23))) // a Wednesday
				.contains(LocalDate.of(2026, 9, 23));
	}

	@Test
	void nextVisitDateRejectsAnUnrecognisedScheduleDayCode() {
		CarePlan plan = publishedPlan(10L, 50L, LocalDate.of(2026, 9, 1));
		repository.save(plan);
		nodeRepository.save(node(plan.id(), "XYZ"));

		assertThatThrownBy(() -> lookup.findNextVisitDate(50L, LocalDate.of(2026, 9, 23)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("XYZ");
	}

	@Test
	void nextVisitDateParsesEveryWeekdayCode() {
		CarePlan plan = publishedPlan(9L, 49L, LocalDate.of(2026, 9, 1));
		repository.save(plan);
		nodeRepository.save(node(plan.id(), "MON,TUE,WED,THU,FRI,SAT,SUN"));

		assertThat(lookup.findNextVisitDate(49L, LocalDate.of(2026, 9, 23))) // a Wednesday
				.contains(LocalDate.of(2026, 9, 23));
	}

	private static CarePlan publishedPlan(long id, long elderId, LocalDate startDate) {
		return new CarePlan(
				id, elderId, 7L, null, 1, CarePlan.Status.PUBLISHED, new BigDecimal("7.5"),
				LocalDateTime.of(2026, 9, 6, 10, 8), LocalDateTime.of(2026, 9, 6, 10, 8),
				LocalDateTime.of(2026, 9, 6, 10, 9), startDate, null, null, null, null);
	}

	private static CarePlanNode node(Long carePlanId, String scheduleDays) {
		return new CarePlanNode(
				null, carePlanId, null, "Task", scheduleDays, new BigDecimal("1.0"), new BigDecimal("2.0"),
				CarePlanNode.EvidenceType.NONE, 1, LocalDateTime.of(2026, 9, 6, 10, 8),
				LocalDateTime.of(2026, 9, 6, 10, 8), List.of());
	}
}
