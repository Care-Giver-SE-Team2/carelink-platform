package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.AbsenceReport;

/**
 * A confirmed absence that the nightly roster has since added visits to has them re-rostered at
 * once, and goes back to the managers for review. The re-rostering itself is
 * AbsenceReRosteringServiceTest's business.
 */
class AbsenceCoverageServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

	private final ReRosteringFakes.Absences absences = new ReRosteringFakes.Absences();
	private final ReRosteringFakes.Changes changes = new ReRosteringFakes.Changes();
	private final ReRosteringFakes.Visits visits = new ReRosteringFakes.Visits();
	private final AbsenceReRosteringService reRostering = mock(AbsenceReRosteringService.class);
	private final ReRosteringFakes.Profiles profiles = new ReRosteringFakes.Profiles().caregiver(5L, "Aisha", false);
	private final List<String> told = new ArrayList<>();
	private final ReRosteringFakes.MutableClock clock =
			new ReRosteringFakes.MutableClock(TODAY.atTime(2, 30), ZoneId.of("Asia/Singapore"));
	private final AbsenceCoverageService service = new AbsenceCoverageService(absences, changes, visits, reRostering,
			profiles, new ReRosteringFakes.AbsenceAlerts(told), clock);

	@Test
	void aConfirmedAbsenceIsLeftAloneWhileNothingIsLeftWithTheAbsentCaregiver() {
		AbsenceReport leave = confirmedLeave(TODAY.plusDays(3), TODAY.plusDays(20));
		visits.add(1L, 7L, 6L, TODAY.plusDays(4).atTime(9, 0), 60)
				.add(4L, 7L, 5L, TODAY.plusDays(21).atTime(9, 0), 60);

		assertThat(service.rerosterAddedVisits(leave.id())).isFalse();
		assertThat(absences.findById(leave.id()).orElseThrow().isCoverageConfirmed()).isTrue();
		verify(reRostering, never()).reroster(any(), any(), any());
		assertThat(told).as("the visit after the leave is the caregiver's own").isEmpty();
	}

	@Test
	void visitsTheRosterAddsOnTheLeaveDaysAreReRosteredByTheSystemAndReviewedOnce() {
		AbsenceReport leave = confirmedLeave(TODAY.plusDays(3), TODAY.plusDays(20));
		visits.add(2L, 7L, 5L, TODAY.plusDays(16).atTime(9, 0), 60)
				.add(3L, 8L, 5L, TODAY.plusDays(16).atTime(14, 0), 60);
		when(reRostering.reroster(leave.id(), null, null))
				.thenReturn(new AbsenceReRosteringService.ReRosterOutcome(leave.id(), 1L, 2, 2, 0, 0));

		assertThat(service.rerosterAddedVisits(leave.id())).isTrue();

		verify(reRostering).reroster(leave.id(), null, null);
		assertThat(absences.findById(leave.id()).orElseThrow().isCoverageConfirmed())
				.as("a manager reviews what the system did and confirms again").isFalse();
		assertThat(told).containsExactly("Aisha " + leave.id() + " offered 2, settled 0, uncovered 0");
		assertThat(service.rerosterAddedVisits(leave.id())).as("told once, not every night").isFalse();
		assertThat(told).hasSize(1);
	}

	@Test
	void onlyConfirmedApprovedAbsencesNotYetOverAreRechecked() {
		AbsenceReport confirmed = confirmedLeave(TODAY.plusDays(3), TODAY.plusDays(20));
		absences.save(AbsenceReport.recordedByManager(5L, null, TODAY.plusDays(30), TODAY.plusDays(31), null, 11L,
				TODAY));
		absences.save(AbsenceReport.requested(5L, null, TODAY.plusDays(40), TODAY.plusDays(41), null, TODAY));
		AbsenceReport endsTomorrow = confirmedLeave(TODAY, TODAY.plusDays(1));

		assertThat(service.confirmedAbsencesStillAhead()).containsExactlyInAnyOrder(confirmed.id(), endsTomorrow.id());

		clock.set(TODAY.plusDays(2).atTime(2, 30));
		assertThat(service.confirmedAbsencesStillAhead()).containsExactly(confirmed.id());
	}

	private AbsenceReport confirmedLeave(LocalDate from, LocalDate until) {
		AbsenceReport approved = absences.save(AbsenceReport.recordedByManager(5L, AbsenceReport.Type.ANNUAL, from,
				until, null, 11L, TODAY));
		return absences.save(approved.coverageConfirmedBy(12L, TODAY.atTime(1, 0)));
	}
}
