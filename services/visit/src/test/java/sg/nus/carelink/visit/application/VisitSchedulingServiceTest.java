package sg.nus.carelink.visit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.visit.application.VisitScheduling.Outcome;
import sg.nus.carelink.visit.application.VisitScheduling.PlannedVisit;
import sg.nus.carelink.visit.domain.model.Visit;

class VisitSchedulingServiceTest {

	private static final LocalDateTime MON_8 = LocalDateTime.of(2026, 10, 5, 8, 0);
	private static final LocalDateTime WED_8 = LocalDateTime.of(2026, 10, 7, 8, 0);

	private final InMemoryVisitRepository visits = new InMemoryVisitRepository();
	private final VisitSchedulingService scheduling = new VisitSchedulingService(visits, visits);

	@Test
	void createsScheduledVisitsLinkedToTheirPlanTask() {
		Outcome outcome = scheduling.schedule(List.of(planned(MON_8, 5L), planned(WED_8, 5L)));

		assertThat(outcome).isEqualTo(new Outcome(2, 0));
		assertThat(visits.findByCarePlanIdStartingFrom(9L, MON_8))
				.allSatisfy(visit -> {
					assertThat(visit.status()).isEqualTo(Visit.Status.SCHEDULED);
					assertThat(visit.carePlanNodeId()).isEqualTo(30L);
					assertThat(visit.caregiverId()).isEqualTo(5L);
					assertThat(visit.serviceType()).isEqualTo("Bathing assistance");
				})
				.extracting(Visit::scheduledStart).containsExactlyInAnyOrder(MON_8, WED_8);
	}

	@Test
	void schedulingTheSameVisitsTwiceCreatesNothingNew() {
		scheduling.schedule(List.of(planned(MON_8, 5L)));

		assertThat(scheduling.schedule(List.of(planned(MON_8, 5L)))).isEqualTo(new Outcome(0, 0));
		assertThat(visits.findByCarePlanIdStartingFrom(9L, MON_8)).hasSize(1);
	}

	@Test
	void givesAnUncoveredVisitToTheCaregiverNowNamed() {
		scheduling.schedule(List.of(planned(MON_8, null)));

		assertThat(scheduling.schedule(List.of(planned(MON_8, 5L)))).isEqualTo(new Outcome(0, 1));
		assertThat(visits.findByCarePlanIdStartingFrom(9L, MON_8)).singleElement()
				.extracting(Visit::caregiverId).isEqualTo(5L);
	}

	@Test
	void leavesAVisitAlreadyGivenToSomeoneElseWithThem() {
		scheduling.schedule(List.of(planned(MON_8, 6L)));

		assertThat(scheduling.schedule(List.of(planned(MON_8, 5L)))).isEqualTo(new Outcome(0, 0));
		assertThat(visits.findByCarePlanIdStartingFrom(9L, MON_8)).singleElement()
				.extracting(Visit::caregiverId).isEqualTo(6L);
	}

	@Test
	void cancelsOnlyUntouchedVisitsFromTheGivenTime() {
		scheduling.schedule(List.of(planned(MON_8, 5L), planned(WED_8, 5L), planned(WED_8.plusDays(1), 5L)));
		Visit started = visits.findByCarePlanIdStartingFrom(9L, WED_8).stream()
				.filter(v -> v.scheduledStart().equals(WED_8)).findFirst().orElseThrow();
		visits.save(new Visit(started.id(), started.elderId(), started.caregiverId(), started.carePlanNodeId(), null,
				started.serviceType(), started.scheduledStart(), started.scheduledEnd(), WED_8, null,
				Visit.Status.IN_PROGRESS, null, started.carePlanId(), 1, null, null));

		assertThat(scheduling.cancelUntouchedFrom(9L, WED_8)).isEqualTo(1);
		assertThat(visits.findByCarePlanIdStartingFrom(9L, MON_8))
				.extracting(Visit::scheduledStart, Visit::status)
				.containsExactlyInAnyOrder(
						org.assertj.core.groups.Tuple.tuple(MON_8, Visit.Status.SCHEDULED),
						org.assertj.core.groups.Tuple.tuple(WED_8, Visit.Status.IN_PROGRESS),
						org.assertj.core.groups.Tuple.tuple(WED_8.plusDays(1), Visit.Status.CANCELLED));
	}

	@Test
	void findsUnassignedVisitsThatHaveStartedAndTurnsThemIntoExceptions() {
		scheduling.schedule(List.of(planned(MON_8, null), planned(WED_8, null), planned(MON_8.plusHours(1), 5L)));

		List<VisitScheduling.UncoveredVisit> uncovered = scheduling.findUncoveredStarted(MON_8.minusDays(1), MON_8.plusHours(2));

		assertThat(uncovered).singleElement().satisfies(visit -> {
			assertThat(visit.start()).isEqualTo(MON_8);
			assertThat(visit.serviceType()).isEqualTo("Bathing assistance");
		});
		assertThat(scheduling.markUncoveredAsException(uncovered.getFirst().visitId())).isTrue();
		assertThat(visits.findById(uncovered.getFirst().visitId())).get()
				.extracting(Visit::status).isEqualTo(Visit.Status.EXCEPTION);
		assertThat(scheduling.findUncoveredStarted(MON_8.minusDays(1), MON_8.plusHours(2))).isEmpty();
	}

	@Test
	void leavesAVisitCoveredInTheMeantimeAlone() {
		scheduling.schedule(List.of(planned(MON_8, null)));
		Long visitId = scheduling.findUncoveredStarted(MON_8.minusDays(1), MON_8.plusHours(1)).getFirst().visitId();
		scheduling.schedule(List.of(planned(MON_8, 5L)));

		assertThat(scheduling.markUncoveredAsException(visitId)).isFalse();
		assertThat(visits.findById(visitId)).get().extracting(Visit::status).isEqualTo(Visit.Status.SCHEDULED);
	}

	private static PlannedVisit planned(LocalDateTime start, Long caregiverId) {
		return new PlannedVisit(42L, caregiverId, 9L, 30L, "Bathing assistance", start, start.plusMinutes(30));
	}
}
