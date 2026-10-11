package sg.nus.carelink.rostering.infrastructure.visit;

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
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import sg.nus.carelink.rostering.domain.repository.VisitReassignment;
import sg.nus.carelink.rostering.domain.repository.VisitScheduling;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visitapi.VisitApi;
import sg.nus.carelink.visitapi.VisitNotFound;
import sg.nus.carelink.visitapi.VisitRuleViolation;

/**
 * Rostering's visit ports on visit's internal API: what visit answers becomes rostering's own
 * records, rostering's changes reach visit as asked, and visit's refusals keep their codes.
 */
class VisitApiRosteringAdaptersTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 12, 9, 0);

	private static final VisitReassignment.Change WHY = new VisitReassignment.Change(33L, 10L, 44L, "Leave");

	private static final VisitApi.Change VISITS_WHY = new VisitApi.Change(33L, 10L, 44L, "Leave");

	private final VisitApi visit = mock(VisitApi.class);

	private final VisitApiVisitReassignment reassignment = new VisitApiVisitReassignment(visit);

	private final VisitApiVisitScheduling scheduling = new VisitApiVisitScheduling(visit);

	@Test
	void visitsAnswersBecomeRosteringsSlotsAndBookings() {
		when(visit.findVisit(812L)).thenReturn(Optional.of(
				new VisitApi.VisitSlot(812L, 101L, 7L, 21L, "BATHING", NINE, NINE.plusHours(1), "SCHEDULED", null)));
		when(visit.unstartedFor(7L, NINE, NINE.plusDays(1))).thenReturn(List.of(
				new VisitApi.VisitSlot(813L, 101L, 7L, 21L, "MEALS", NINE.plusHours(3), null, "SCHEDULED", 33L)));
		when(visit.bookingsBetween(NINE, NINE.plusDays(1)))
				.thenReturn(List.of(new VisitApi.Booking(812L, 101L, 7L, NINE, NINE.plusHours(1))));
		when(visit.finishedVisitsWith(101L, NINE.minusDays(30), NINE)).thenReturn(Map.of(7L, 3));

		assertThat(reassignment.find(812L)).contains(
				new VisitReassignment.VisitSlot(812L, 101L, 7L, 21L, "BATHING", NINE, NINE.plusHours(1), "SCHEDULED", null));
		assertThat(reassignment.find(999L)).isEmpty();
		assertThat(reassignment.unstartedFor(7L, NINE, NINE.plusDays(1))).containsExactly(
				new VisitReassignment.VisitSlot(813L, 101L, 7L, 21L, "MEALS", NINE.plusHours(3), null, "SCHEDULED", 33L));
		assertThat(reassignment.bookingsBetween(NINE, NINE.plusDays(1)))
				.containsExactly(new VisitReassignment.Booking(812L, 101L, 7L, NINE, NINE.plusHours(1)));
		assertThat(reassignment.finishedVisitsWith(101L, NINE.minusDays(30), NINE)).containsEntry(7L, 3);
	}

	@Test
	void rosteringsChangesReachVisitAsAskedWithTheirRequestKeys() {
		when(visit.moveTo(eq(812L), any(), any())).thenReturn(new VisitApi.VisitRef(900L));

		reassignment.reassign(812L, 9L, WHY, "step-1");
		reassignment.markUncovered(813L, WHY, "step-2");
		reassignment.callOff(814L, WHY, "step-3");
		Long moved = reassignment.moveTo(812L, NINE, 9L, WHY, "step-4");
		reassignment.reassign(815L, 9L, WHY);

		verify(visit).reassign(812L, new VisitApi.Reassignment(9L, VISITS_WHY), "step-1");
		verify(visit).markUncovered(813L, VISITS_WHY, "step-2");
		verify(visit).callOff(814L, VISITS_WHY, "step-3");
		verify(visit).moveTo(812L, new VisitApi.Move(NINE, 9L, VISITS_WHY), "step-4");
		verify(visit).reassign(815L, new VisitApi.Reassignment(9L, VISITS_WHY), null);
		assertThat(moved).isEqualTo(900L);
	}

	@Test
	void visitsRefusalKeepsItsCodeAndAMissingVisitIsNotFound() {
		doThrow(new VisitRuleViolation("VISIT_NOT_OPEN", "The visit has started.")).when(visit).reassign(eq(812L), any(), any());
		doThrow(new VisitNotFound("Visit [999] does not exist")).when(visit).callOff(eq(999L), any(), any());
		when(visit.moveTo(eq(998L), any(), any())).thenThrow(new VisitNotFound("Visit [998] does not exist"));

		assertThatThrownBy(() -> reassignment.reassign(812L, 9L, WHY))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessage("The visit has started.")
				.extracting(error -> ((BusinessRuleViolation) error).code()).isEqualTo("VISIT_NOT_OPEN");
		assertThatThrownBy(() -> reassignment.callOff(999L, WHY, "step-5"))
				.isInstanceOf(ResourceNotFound.class)
				.hasMessage("Visit [999] does not exist");
		assertThatThrownBy(() -> reassignment.moveTo(998L, NINE, 9L, WHY, "step-6")).isInstanceOf(ResourceNotFound.class);
	}

	@Test
	void schedulingIsVisitsAnswer() {
		when(visit.schedule(List.of(new VisitApi.PlannedVisit(101L, 7L, 21L, 40L, "BATHING", NINE, NINE.plusHours(1)))))
				.thenReturn(new VisitApi.Outcome(1, 0));
		when(visit.cancelUntouchedFrom(new VisitApi.CancelUntouched(21L, NINE))).thenReturn(new VisitApi.Cancelled(2));
		when(visit.findUncoveredStarted(NINE.minusHours(1), NINE))
				.thenReturn(List.of(new VisitApi.UncoveredVisit(812L, 101L, NINE, "BATHING")));
		when(visit.markUncoveredAsException(812L)).thenReturn(new VisitApi.Marked(true));

		assertThat(scheduling.schedule(List.of(
				new VisitScheduling.PlannedVisit(101L, 7L, 21L, 40L, "BATHING", NINE, NINE.plusHours(1)))))
				.isEqualTo(new VisitScheduling.Outcome(1, 0));
		assertThat(scheduling.cancelUntouchedFrom(21L, NINE)).isEqualTo(2);
		assertThat(scheduling.findUncoveredStarted(NINE.minusHours(1), NINE))
				.containsExactly(new VisitScheduling.UncoveredVisit(812L, 101L, NINE, "BATHING"));
		assertThat(scheduling.markUncoveredAsException(812L)).isTrue();
	}

}
