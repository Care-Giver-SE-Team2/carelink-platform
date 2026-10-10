package sg.nus.carelink.report.infrastructure.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import sg.nus.carelink.report.application.StandaloneVisits;
import sg.nus.carelink.report.application.VisitReassignment;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visitapi.VisitApi;
import sg.nus.carelink.visitapi.VisitNotFound;
import sg.nus.carelink.visitapi.VisitRuleViolation;

/**
 * report's adapters on visit's internal API hand report what visit answers, in report's own
 * types, and visit's refusals become the errors visit threw when it ran in the same process.
 */
class VisitAdaptersTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 12, 9, 0);

	private final VisitApi visit = mock(VisitApi.class);

	@Test
	void aStandaloneVisitIsScheduledAndReadBackFromVisit() {
		VisitApiStandaloneVisits visits = new VisitApiStandaloneVisits(visit);
		when(visit.scheduleStandalone(new VisitApi.NewVisit(101L, 9L, "Hospital escort", NINE, NINE.plusHours(2),
				"Bring the letter"))).thenReturn(new VisitApi.VisitRef(812L));
		when(visit.findVisitState(812L)).thenReturn(Optional.of(new VisitApi.State(812L, 9L, "SCHEDULED", false)));
		when(visit.findVisitState(999L)).thenReturn(Optional.empty());

		assertThat(visits.schedule(new StandaloneVisits.NewVisit(101L, 9L, "Hospital escort", NINE, NINE.plusHours(2),
				"Bring the letter"))).isEqualTo(812L);
		assertThat(visits.find(812L)).contains(new StandaloneVisits.State(812L, 9L, "SCHEDULED", false));
		assertThat(visits.find(999L)).isEmpty();
	}

	@Test
	void aStandaloneVisitThatVisitRefusesKeepsItsCode() {
		when(visit.scheduleStandalone(any())).thenThrow(new VisitRuleViolation("VISIT_IN_THE_PAST", "Too late."));

		assertThatThrownBy(() -> new VisitApiStandaloneVisits(visit)
				.schedule(new StandaloneVisits.NewVisit(101L, null, "Hospital escort", NINE, NINE.plusHours(2), null)))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(error -> ((BusinessRuleViolation) error).code()).isEqualTo("VISIT_IN_THE_PAST");
	}

	@Test
	void whoVisitsAnElderIsVisitsAnswer() {
		VisitApiVisitScheduleQuery schedule = new VisitApiVisitScheduleQuery(visit);
		when(visit.hasAssignedVisit(Set.of(101L), 9L)).thenReturn(new VisitApi.Assigned(true));
		when(visit.caregiverIdsForElder(101L)).thenReturn(List.of(9L, 5L));

		assertThat(schedule.hasAssignedVisit(Set.of(101L), 9L)).isTrue();
		assertThat(schedule.caregiverIdsForElder(101L)).containsExactly(9L, 5L);
	}

	@Test
	void callingOffAVisitPassesWhoAndWhy() {
		new VisitApiVisitReassignment(visit).callOff(812L, new VisitReassignment.Change(null, 10L, null, "Cancelled"));

		verify(visit).callOff(812L, new VisitApi.Change(null, 10L, null, "Cancelled"));
	}

	@Test
	void callingOffKeepsVisitsRefusalAndAMissingVisitIsNotFound() {
		VisitApiVisitReassignment reassignment = new VisitApiVisitReassignment(visit);
		doThrow(new VisitRuleViolation("VISIT_NOT_OPEN", "The visit has started.")).when(visit).callOff(eq(812L), any());
		doThrow(new VisitNotFound("Visit [999] does not exist")).when(visit).callOff(eq(999L), any());
		VisitReassignment.Change why = new VisitReassignment.Change(null, 10L, null, "Cancelled");

		assertThatThrownBy(() -> reassignment.callOff(812L, why))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(error -> ((BusinessRuleViolation) error).code()).isEqualTo("VISIT_NOT_OPEN");
		assertThatThrownBy(() -> reassignment.callOff(999L, why))
				.isInstanceOf(ResourceNotFound.class)
				.hasMessage("Visit [999] does not exist");
	}

}
