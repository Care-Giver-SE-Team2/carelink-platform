package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.careplan.application.CarePlanSchedules;
import sg.nus.carelink.careplan.application.CarePlanSchedules.PlanSchedule;
import sg.nus.carelink.careplan.application.CarePlanSchedules.Slot;
import sg.nus.carelink.careplan.application.CarePlanSchedules.Task;
import sg.nus.carelink.rostering.application.RecurringRosterService.RosterRefresh;
import sg.nus.carelink.rostering.domain.repository.VisitScheduling;
import sg.nus.carelink.rostering.domain.repository.VisitScheduling.PlannedVisit;

class RecurringRosterServiceTest {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
	// Thursday 1 Oct 2026, 09:00.
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);
	private static final LocalDate THU_1 = NOW.toLocalDate();

	private final Map<Long, List<PlanSchedule>> plans = new java.util.HashMap<>();
	private final Map<Long, Long> primaryCaregivers = new java.util.HashMap<>();
	private final RecordingVisitScheduling visits = new RecordingVisitScheduling();

	private final RecurringRosterService roster = new RecurringRosterService(
			new CarePlanSchedules() {
				@Override
				public List<PlanSchedule> forElder(Long elderId) {
					return plans.getOrDefault(elderId, List.of());
				}

				@Override
				public List<Long> eldersWithSchedules() {
					return List.copyOf(plans.keySet());
				}
			},
			elderId -> Optional.ofNullable(primaryCaregivers.get(elderId)),
			visits,
			Clock.fixed(NOW.atZone(SINGAPORE).toInstant(), SINGAPORE),
			14);

	@Test
	void schedulesTwoWeeksOfVisitsForThePrimaryCaregiver() {
		plans.put(42L, List.of(plan(9L, THU_1, null, Map.of(DayOfWeek.MONDAY, LocalTime.of(8, 0)))));
		primaryCaregivers.put(42L, 5L);

		RosterRefresh refresh = roster.refreshElder(42L);

		assertThat(visits.scheduled).extracting(PlannedVisit::start).containsExactly(
				LocalDateTime.of(2026, 10, 5, 8, 0), LocalDateTime.of(2026, 10, 12, 8, 0));
		assertThat(visits.scheduled).allSatisfy(visit -> {
			assertThat(visit.caregiverId()).isEqualTo(5L);
			assertThat(visit.carePlanId()).isEqualTo(9L);
			assertThat(visit.carePlanNodeId()).isEqualTo(90L);
			assertThat(visit.end()).isEqualTo(visit.start().plusMinutes(30));
		});
		assertThat(refresh).isEqualTo(new RosterRefresh(42L, 2, 0, 0));
		assertThat(visits.cancelled).isEmpty();
	}

	@Test
	void leavesVisitsUnassignedWhenTheElderHasNoPrimaryCaregiver() {
		plans.put(42L, List.of(plan(9L, THU_1, null, Map.of(DayOfWeek.MONDAY, LocalTime.of(8, 0)))));

		roster.refreshElder(42L);

		assertThat(visits.scheduled).isNotEmpty().allSatisfy(visit -> assertThat(visit.caregiverId()).isNull());
	}

	@Test
	void handsOverFromTheReplacedVersionOnTheNewOnesStartDate() {
		LocalDate monday = LocalDate.of(2026, 10, 5);
		plans.put(42L, List.of(
				plan(8L, THU_1.minusDays(30), monday, Map.of(DayOfWeek.FRIDAY, LocalTime.of(10, 0))),
				plan(9L, monday, null, Map.of(DayOfWeek.MONDAY, LocalTime.of(8, 0)))));

		RosterRefresh refresh = roster.refreshElder(42L);

		assertThat(visits.cancelled).containsExactly(Map.entry(8L, monday.atStartOfDay()));
		assertThat(visits.scheduled).extracting(PlannedVisit::carePlanId, PlannedVisit::start).containsExactly(
				org.assertj.core.groups.Tuple.tuple(8L, LocalDateTime.of(2026, 10, 2, 10, 0)),
				org.assertj.core.groups.Tuple.tuple(9L, LocalDateTime.of(2026, 10, 5, 8, 0)),
				org.assertj.core.groups.Tuple.tuple(9L, LocalDateTime.of(2026, 10, 12, 8, 0)));
		assertThat(refresh.cancelled()).isEqualTo(1);
	}

	@Test
	void aStoppedPlanCallsOffItsRemainingVisitsAndSchedulesNoMore() {
		plans.put(42L, List.of(plan(9L, THU_1.minusDays(30), THU_1, Map.of(DayOfWeek.MONDAY, LocalTime.of(8, 0)))));

		roster.refreshElder(42L);

		assertThat(visits.cancelled).containsExactly(Map.entry(9L, NOW));
		assertThat(visits.scheduled).isEmpty();
	}

	@Test
	void refreshesEveryElderWithAPlan() {
		plans.put(42L, List.of());
		plans.put(43L, List.of());

		assertThat(roster.eldersToRefresh()).containsExactlyInAnyOrder(42L, 43L);
	}

	private static PlanSchedule plan(Long id, LocalDate from, LocalDate until, Map<DayOfWeek, LocalTime> days) {
		List<Slot> slots = days.entrySet().stream().map(e -> new Slot(e.getKey(), e.getValue(), 30)).toList();
		return new PlanSchedule(id, 42L, 1, from, until,
				List.of(new Task(id * 10, "Personal care", "BATHING", "Bathing assistance", slots)));
	}

	private static final class RecordingVisitScheduling implements VisitScheduling {
		final List<PlannedVisit> scheduled = new ArrayList<>();
		final List<Map.Entry<Long, LocalDateTime>> cancelled = new ArrayList<>();

		@Override
		public Outcome schedule(List<PlannedVisit> visits) {
			scheduled.addAll(visits);
			return new Outcome(visits.size(), 0);
		}

		@Override
		public int cancelUntouchedFrom(Long carePlanId, LocalDateTime from) {
			cancelled.add(Map.entry(carePlanId, from));
			return 1;
		}

		@Override
		public List<UncoveredVisit> findUncoveredStarted(LocalDateTime since, LocalDateTime now) {
			return List.of();
		}

		@Override
		public boolean markUncoveredAsException(Long visitId) {
			return false;
		}
	}
}
