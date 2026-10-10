package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.support.IncidentFixtures;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;
import sg.nus.carelink.rostering.domain.repository.VisitReassignment;

/**
 * The last safety net for leave: a visit due within a day that is still with the caregiver who
 * is away gets one incident, so a manager re-rosters it while there is time. Visits already
 * re-rostered, covered, further off, or already raised are left alone.
 */
class LeaveReminderServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 0);
	private static final LocalDate LEAVE_DAY = LocalDate.of(2026, 10, 8);

	private final ReRosteringFakes.MutableClock clock = new ReRosteringFakes.MutableClock(NOW, ZoneId.of("Asia/Singapore"));
	private final ReRosteringFakes.Absences absences = new ReRosteringFakes.Absences();
	private final ReRosteringFakes.Changes changes = new ReRosteringFakes.Changes();
	private final ReRosteringFakes.Visits visits = new ReRosteringFakes.Visits();
	private final ReRosteringFakes.Profiles profiles = new ReRosteringFakes.Profiles().caregiver(5L, "Aisha", false);
	private final IncidentService incidents = mock(IncidentService.class);
	private final LeaveReminderService service = new LeaveReminderService(absences, changes, visits, incidents, profiles,
			clock, Duration.ofHours(24));

	private Long absenceId;

	@BeforeEach
	void setUp() {
		absenceId = absences.save(AbsenceReport.recordedByManager(5L, AbsenceReport.Type.SICK, LEAVE_DAY,
				LEAVE_DAY.plusDays(2), null, 11L, NOW.toLocalDate())).id();
		visits.add(30L, 7L, 5L, LEAVE_DAY.atTime(9, 0), 45) // within a day
				.add(31L, 8L, 5L, LEAVE_DAY.atTime(11, 30), 60) // within a day
				.add(32L, 7L, 5L, LEAVE_DAY.plusDays(1).atTime(9, 0), 45) // two days off
				.add(33L, 7L, 9L, LEAVE_DAY.atTime(10, 0), 60); // somebody else's
		when(incidents.forElder(any())).thenReturn(List.of());
	}

	@Test
	void aVisitDueWithinADayStillWithTheAbsentCaregiverGetsAnIncident() {
		List<LeaveReminderService.DueVisit> due = service.dueStillOnLeave();

		assertThat(due).extracting(LeaveReminderService.DueVisit::visitId).containsExactly(30L, 31L);
		assertThat(service.remind(due.get(0))).isTrue();
		verify(incidents).raiseForUnrosteredLeaveVisit(eq(7L), eq(30L), contains(
				"Personal care visit at 8 Oct 09:00 is still with Aisha, who is on approved leave; re-roster it on the Absences screen (absence "
						+ absenceId + ")"));
	}

	@Test
	void aVisitAlreadyReRosteredOrHandedToACoverIsNotDue() {
		VisitReassignment.VisitSlot visit = visits.rows.get(30L);
		changes.save(RosterChange.offered(absenceId, new VacatedSlot(30L, 7L, visit.carePlanId(), visit.serviceType(),
				visit.start(), visit.end(), 5L), null, 9L, NOW.plusHours(2), NOW));
		visits.reassign(31L, 9L, new VisitReassignment.Change(absenceId, null, null, "covered"));

		assertThat(service.dueStillOnLeave()).isEmpty();
	}

	@Test
	void oneIncidentPerVisitEvenAfterTheManagerClosesIt() {
		LeaveReminderService.DueVisit due = service.dueStillOnLeave().get(0);
		Incident earlier = IncidentFixtures.withId(Incident.raisedForUncoveredVisit(7L, 30L, "earlier", NOW), 900L);
		when(incidents.forElder(7L)).thenReturn(List.of(earlier));

		assertThat(service.remind(due)).isFalse();
		verify(incidents, never()).raiseForUnrosteredLeaveVisit(any(), any(), any());
	}

	@Test
	void aVisitThatLeftTheAbsentCaregiverMeanwhileIsNotRaised() {
		LeaveReminderService.DueVisit due = service.dueStillOnLeave().get(0);
		visits.reassign(30L, 9L, new VisitReassignment.Change(absenceId, 12L, null, "a manager took it"));

		assertThat(service.remind(due)).isFalse();
		verify(incidents, never()).raiseForUnrosteredLeaveVisit(any(), any(), any());
	}

	@Test
	void leaveThatStartsMoreThanADayAwayIsNotDueYet() {
		clock.set(NOW.minusDays(2));

		assertThat(service.dueStillOnLeave()).isEmpty();
	}
}
