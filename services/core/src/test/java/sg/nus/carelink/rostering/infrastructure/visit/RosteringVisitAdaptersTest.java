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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import sg.nus.carelink.rostering.domain.repository.VisitReassignment;
import sg.nus.carelink.rostering.domain.repository.VisitScheduling;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visitapi.VisitApi;
import sg.nus.carelink.visitapi.VisitNotFound;
import sg.nus.carelink.visitapi.VisitRuleViolation;

/**
 * Exactly one adapter of each of rostering's visit ports is active: visit's services in core until
 * {@code carelink.visit-api.base-url} is set, visit's internal API from then on. Both hand
 * rostering the same answers, and visit's refusals keep their codes.
 */
class RosteringVisitAdaptersTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 12, 9, 0);

	private static final VisitReassignment.Change WHY = new VisitReassignment.Change(33L, 10L, 44L, "Leave");

	private final sg.nus.carelink.visit.application.VisitReassignment reassignmentInCore =
			mock(sg.nus.carelink.visit.application.VisitReassignment.class);

	private final sg.nus.carelink.visit.application.VisitScheduling schedulingInCore =
			mock(sg.nus.carelink.visit.application.VisitScheduling.class);

	private final VisitApi overHttp = mock(VisitApi.class);

	private final ApplicationContextRunner core = new ApplicationContextRunner()
			.withBean(sg.nus.carelink.visit.application.VisitReassignment.class, () -> reassignmentInCore)
			.withBean(sg.nus.carelink.visit.application.VisitScheduling.class, () -> schedulingInCore)
			.withBean(VisitApi.class, () -> overHttp)
			.withUserConfiguration(InProcessVisitReassignment.class, VisitApiVisitReassignment.class,
					InProcessVisitScheduling.class, VisitApiVisitScheduling.class);

	@Test
	void whileVisitRunsInCoreRosteringAsksVisitsServices() {
		core.run(context -> {
			assertThat(context).getBean(VisitReassignment.class).isInstanceOf(InProcessVisitReassignment.class);
			assertThat(context).getBean(VisitScheduling.class).isInstanceOf(InProcessVisitScheduling.class);
		});
	}

	@Test
	void onceVisitsAddressIsSetRosteringAsksVisitsApi() {
		core.withPropertyValues("carelink.visit-api.base-url=http://visit:8080").run(context -> {
			assertThat(context).getBean(VisitReassignment.class).isInstanceOf(VisitApiVisitReassignment.class);
			assertThat(context).getBean(VisitScheduling.class).isInstanceOf(VisitApiVisitScheduling.class);
		});
	}

	@Test
	void bothReassignmentAdaptersReadTheSameVisits() {
		when(reassignmentInCore.find(812L)).thenReturn(Optional.of(new sg.nus.carelink.visit.application.VisitReassignment
				.VisitSlot(812L, 101L, 7L, 21L, "BATHING", NINE, NINE.plusHours(1), "SCHEDULED", null)));
		when(reassignmentInCore.unstartedFor(7L, NINE, NINE.plusDays(1))).thenReturn(List.of());
		when(reassignmentInCore.bookingsBetween(NINE, NINE.plusDays(1))).thenReturn(List.of(
				new sg.nus.carelink.visit.application.VisitReassignment.Booking(812L, 101L, 7L, NINE, NINE.plusHours(1))));
		when(reassignmentInCore.finishedVisitsWith(101L, NINE.minusDays(30), NINE)).thenReturn(Map.of(7L, 3));
		when(overHttp.findVisit(812L)).thenReturn(Optional.of(
				new VisitApi.VisitSlot(812L, 101L, 7L, 21L, "BATHING", NINE, NINE.plusHours(1), "SCHEDULED", null)));
		when(overHttp.unstartedFor(7L, NINE, NINE.plusDays(1))).thenReturn(List.of());
		when(overHttp.bookingsBetween(NINE, NINE.plusDays(1)))
				.thenReturn(List.of(new VisitApi.Booking(812L, 101L, 7L, NINE, NINE.plusHours(1))));
		when(overHttp.finishedVisitsWith(101L, NINE.minusDays(30), NINE)).thenReturn(Map.of(7L, 3));
		VisitReassignment.VisitSlot slot =
				new VisitReassignment.VisitSlot(812L, 101L, 7L, 21L, "BATHING", NINE, NINE.plusHours(1), "SCHEDULED", null);
		VisitReassignment.Booking booking = new VisitReassignment.Booking(812L, 101L, 7L, NINE, NINE.plusHours(1));

		for (VisitReassignment adapter : List.of(new InProcessVisitReassignment(reassignmentInCore),
				new VisitApiVisitReassignment(overHttp))) {
			assertThat(adapter.find(812L)).contains(slot);
			assertThat(adapter.unstartedFor(7L, NINE, NINE.plusDays(1))).isEmpty();
			assertThat(adapter.bookingsBetween(NINE, NINE.plusDays(1))).containsExactly(booking);
			assertThat(adapter.finishedVisitsWith(101L, NINE.minusDays(30), NINE)).containsEntry(7L, 3);
		}
	}

	@Test
	void bothReassignmentAdaptersMakeTheSameChanges() {
		when(reassignmentInCore.moveTo(eq(812L), eq(NINE), eq(9L), any())).thenReturn(900L);
		when(overHttp.moveTo(eq(812L), any())).thenReturn(new VisitApi.VisitRef(900L));
		var inProcess = new InProcessVisitReassignment(reassignmentInCore);
		var overApi = new VisitApiVisitReassignment(overHttp);
		var visitsWhy = new sg.nus.carelink.visit.application.VisitReassignment.Change(33L, 10L, 44L, "Leave");
		var apiWhy = new VisitApi.Change(33L, 10L, 44L, "Leave");

		inProcess.reassign(812L, 9L, WHY);
		inProcess.markUncovered(813L, WHY);
		inProcess.callOff(814L, WHY);
		assertThat(inProcess.moveTo(812L, NINE, 9L, WHY)).isEqualTo(900L);
		overApi.reassign(812L, 9L, WHY);
		overApi.markUncovered(813L, WHY);
		overApi.callOff(814L, WHY);
		assertThat(overApi.moveTo(812L, NINE, 9L, WHY)).isEqualTo(900L);

		verify(reassignmentInCore).reassign(812L, 9L, visitsWhy);
		verify(reassignmentInCore).markUncovered(813L, visitsWhy);
		verify(reassignmentInCore).callOff(814L, visitsWhy);
		verify(overHttp).reassign(812L, new VisitApi.Reassignment(9L, apiWhy));
		verify(overHttp).markUncovered(813L, apiWhy);
		verify(overHttp).callOff(814L, apiWhy);
		verify(overHttp).moveTo(812L, new VisitApi.Move(NINE, 9L, apiWhy));
	}

	@Test
	void visitsRefusalKeepsItsCodeAndAMissingVisitIsNotFound() {
		var overApi = new VisitApiVisitReassignment(overHttp);
		doThrow(new VisitRuleViolation("VISIT_NOT_OPEN", "The visit has started.")).when(overHttp).reassign(eq(812L), any());
		doThrow(new VisitNotFound("Visit [999] does not exist")).when(overHttp).callOff(eq(999L), any());

		assertThatThrownBy(() -> overApi.reassign(812L, 9L, WHY))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessage("The visit has started.")
				.extracting(error -> ((BusinessRuleViolation) error).code()).isEqualTo("VISIT_NOT_OPEN");
		assertThatThrownBy(() -> overApi.callOff(999L, WHY))
				.isInstanceOf(ResourceNotFound.class)
				.hasMessage("Visit [999] does not exist");
	}

	@Test
	void bothSchedulingAdaptersScheduleAndSweepTheSame() {
		var planned = new VisitScheduling.PlannedVisit(101L, 7L, 21L, 40L, "BATHING", NINE, NINE.plusHours(1));
		when(schedulingInCore.schedule(List.of(new sg.nus.carelink.visit.application.VisitScheduling.PlannedVisit(101L, 7L,
				21L, 40L, "BATHING", NINE, NINE.plusHours(1))))).thenReturn(
						new sg.nus.carelink.visit.application.VisitScheduling.Outcome(1, 0));
		when(schedulingInCore.cancelUntouchedFrom(21L, NINE)).thenReturn(2);
		when(schedulingInCore.findUncoveredStarted(NINE.minusHours(1), NINE)).thenReturn(List.of(
				new sg.nus.carelink.visit.application.VisitScheduling.UncoveredVisit(812L, 101L, NINE, "BATHING")));
		when(schedulingInCore.markUncoveredAsException(812L)).thenReturn(true);
		when(overHttp.schedule(List.of(new VisitApi.PlannedVisit(101L, 7L, 21L, 40L, "BATHING", NINE, NINE.plusHours(1)))))
				.thenReturn(new VisitApi.Outcome(1, 0));
		when(overHttp.cancelUntouchedFrom(new VisitApi.CancelUntouched(21L, NINE))).thenReturn(new VisitApi.Cancelled(2));
		when(overHttp.findUncoveredStarted(NINE.minusHours(1), NINE))
				.thenReturn(List.of(new VisitApi.UncoveredVisit(812L, 101L, NINE, "BATHING")));
		when(overHttp.markUncoveredAsException(812L)).thenReturn(new VisitApi.Marked(true));

		for (VisitScheduling adapter : List.of(new InProcessVisitScheduling(schedulingInCore),
				new VisitApiVisitScheduling(overHttp))) {
			assertThat(adapter.schedule(List.of(planned))).isEqualTo(new VisitScheduling.Outcome(1, 0));
			assertThat(adapter.cancelUntouchedFrom(21L, NINE)).isEqualTo(2);
			assertThat(adapter.findUncoveredStarted(NINE.minusHours(1), NINE))
					.containsExactly(new VisitScheduling.UncoveredVisit(812L, 101L, NINE, "BATHING"));
			assertThat(adapter.markUncoveredAsException(812L)).isTrue();
		}
	}

}
