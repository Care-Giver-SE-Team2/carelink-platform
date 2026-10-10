package sg.nus.carelink.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.client.MockMvcClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.visit.application.StandaloneVisits;
import sg.nus.carelink.visit.application.UpcomingAssignments;
import sg.nus.carelink.visit.application.VisitLookups;
import sg.nus.carelink.visit.application.VisitReassignment;
import sg.nus.carelink.visit.application.VisitScheduling;
import sg.nus.carelink.visit.controller.InternalVisitPlanningController;
import sg.nus.carelink.visit.controller.InternalVisitQueryController;
import sg.nus.carelink.visit.controller.InternalVisitReassignmentController;
import sg.nus.carelink.visit.domain.repository.VisitScheduleQuery;
import sg.nus.carelink.visitapi.VisitApi;
import sg.nus.carelink.visitapi.VisitApiClients;
import sg.nus.carelink.visitapi.VisitRuleViolation;

/**
 * Both sides of visit's internal API in one test, as CoreApiContractTest does for core's. The client
 * rostering, report and incident will use (visit-api's {@link VisitApi}) calls the controllers core
 * serves it with through MockMvc, so a path, a parameter or a field that differs between the two
 * fails here. visit's services behind the controllers are mocks: each test checks that a call
 * reaches them with the caller's values, and that their answer, or their error, comes back the way
 * the caller expects. When visit moves out, the controllers go with it and this test with them.
 */
@WebMvcTest(controllers = {InternalVisitReassignmentController.class, InternalVisitPlanningController.class,
		InternalVisitQueryController.class})
@Import(InternalApiSecurity.class)
class VisitApiContractTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 12, 9, 0);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private VisitReassignment reassignment;

	@MockitoBean
	private VisitScheduling scheduling;

	@MockitoBean
	private UpcomingAssignments assignments;

	@MockitoBean
	private StandaloneVisits standalone;

	@MockitoBean
	private VisitScheduleQuery schedule;

	@MockitoBean
	private VisitLookups lookups;

	private VisitApi visit;

	@BeforeEach
	void client() {
		visit = VisitApiClients.create(RestClient.builder()
				.requestFactory(new MockMvcClientHttpRequestFactory(mvc))
				.baseUrl("http://visit"));
	}

	@Test
	void aVisitAndTheOnesAnAbsenceVacates() {
		var slot = new VisitReassignment.VisitSlot(100L, 3L, 7L, 21L, "BATHING", NINE, NINE.plusHours(1), "SCHEDULED", null);
		when(reassignment.find(100L)).thenReturn(Optional.of(slot));
		when(reassignment.unstartedFor(7L, NINE, NINE.plusDays(2))).thenReturn(List.of(slot));

		var expected = new VisitApi.VisitSlot(100L, 3L, 7L, 21L, "BATHING", NINE, NINE.plusHours(1), "SCHEDULED", null);
		assertThat(visit.visit(100L)).isEqualTo(expected);
		assertThat(visit.unstartedFor(7L, NINE, NINE.plusDays(2))).containsExactly(expected);
		assertThat(visit.findVisit(101L)).isEmpty();
	}

	@Test
	void whoIsBookedWhenAndWhoHasBeenToAnElder() {
		when(reassignment.bookingsBetween(NINE, NINE.plusDays(1)))
				.thenReturn(List.of(new VisitReassignment.Booking(100L, 3L, 7L, NINE, NINE.plusHours(1))));
		when(reassignment.finishedVisitsWith(3L, NINE.minusDays(90), NINE)).thenReturn(Map.of(7L, 4, 9L, 1));

		assertThat(visit.bookingsBetween(NINE, NINE.plusDays(1)))
				.containsExactly(new VisitApi.Booking(100L, 3L, 7L, NINE, NINE.plusHours(1)));
		assertThat(visit.finishedVisitsWith(3L, NINE.minusDays(90), NINE)).isEqualTo(Map.of(7L, 4, 9L, 1));
	}

	@Test
	void theFourChangesReRosteringMakes() {
		var change = new VisitReassignment.Change(5L, 1L, 12L, "Annual leave");
		when(reassignment.moveTo(100L, NINE.plusDays(1), 9L, change)).thenReturn(101L);

		var why = new VisitApi.Change(5L, 1L, 12L, "Annual leave");
		visit.reassign(100L, new VisitApi.Reassignment(9L, why));
		visit.markUncovered(100L, why);
		visit.callOff(100L, why);
		assertThat(visit.moveTo(100L, new VisitApi.Move(NINE.plusDays(1), 9L, why)).visitId()).isEqualTo(101L);

		verify(reassignment).reassign(100L, 9L, change);
		verify(reassignment).markUncovered(100L, change);
		verify(reassignment).callOff(100L, change);
	}

	@Test
	void aVisitThatCannotChangeKeepsVisitsReason() {
		doThrow(new BusinessRuleViolation("VISIT_NOT_OPEN", "Visit 100 has started and cannot change hands"))
				.when(reassignment).reassign(eq(100L), eq(9L), any());

		var why = new VisitApi.Change(5L, 1L, null, "Annual leave");
		assertThatThrownBy(() -> visit.reassign(100L, new VisitApi.Reassignment(9L, why)))
				.isInstanceOfSatisfying(VisitRuleViolation.class,
						violation -> assertThat(violation.code()).isEqualTo("VISIT_NOT_OPEN"));
	}

	@Test
	void theVisitsACarePlanCallsForAndTheOnesNobodyCovered() {
		var planned = new VisitScheduling.PlannedVisit(3L, null, 21L, 40L, "BATHING", NINE, NINE.plusHours(1));
		when(scheduling.schedule(List.of(planned))).thenReturn(new VisitScheduling.Outcome(1, 0));
		when(scheduling.cancelUntouchedFrom(20L, NINE)).thenReturn(3);
		when(scheduling.findUncoveredStarted(NINE.minusHours(1), NINE))
				.thenReturn(List.of(new VisitScheduling.UncoveredVisit(100L, 3L, NINE.minusMinutes(30), "BATHING")));
		when(scheduling.markUncoveredAsException(100L)).thenReturn(true);
		when(assignments.unstartedBetween(NINE, NINE.plusDays(30)))
				.thenReturn(List.of(new UpcomingAssignments.Assignment(100L, 7L, 21L, NINE)));

		assertThat(visit.schedule(List.of(new VisitApi.PlannedVisit(3L, null, 21L, 40L, "BATHING", NINE,
				NINE.plusHours(1))))).isEqualTo(new VisitApi.Outcome(1, 0));
		assertThat(visit.cancelUntouchedFrom(new VisitApi.CancelUntouched(20L, NINE)).count()).isEqualTo(3);
		assertThat(visit.findUncoveredStarted(NINE.minusHours(1), NINE))
				.containsExactly(new VisitApi.UncoveredVisit(100L, 3L, NINE.minusMinutes(30), "BATHING"));
		assertThat(visit.markUncoveredAsException(100L).marked()).isTrue();
		assertThat(visit.unstartedBetween(NINE, NINE.plusDays(30)))
				.containsExactly(new VisitApi.Assignment(100L, 7L, 21L, NINE));
	}

	@Test
	void anExtraServicesWorkOrderAndWhereItStands() {
		when(standalone.schedule(new StandaloneVisits.NewVisit(3L, null, "ESCORT", NINE, null, "Bring the clinic card")))
				.thenReturn(102L);
		when(standalone.find(102L)).thenReturn(Optional.of(new StandaloneVisits.State(102L, 7L, "SCHEDULED", false)));

		assertThat(visit.scheduleStandalone(new VisitApi.NewVisit(3L, null, "ESCORT", NINE, null,
				"Bring the clinic card")).visitId()).isEqualTo(102L);
		assertThat(visit.findVisitState(102L)).contains(new VisitApi.State(102L, 7L, "SCHEDULED", false));
		assertThat(visit.findVisitState(103L)).isEmpty();
	}

	@Test
	void anEldersCaregiversAndTheirComingVisits() {
		when(schedule.caregiverIdsForElder(3L)).thenReturn(List.of(7L, 9L));
		when(schedule.hasAssignedVisit(Set.of(3L, 4L), 7L)).thenReturn(true);
		when(lookups.upcomingVisits(3L, NINE, NINE.plusDays(14)))
				.thenReturn(List.of(new VisitLookups.ElderVisit(100L, 3L, 7L, NINE, "SCHEDULED", "BATHING")));
		when(lookups.latestCaregiverId(3L)).thenReturn(Optional.of(7L));

		assertThat(visit.caregiverIdsForElder(3L)).containsExactly(7L, 9L);
		assertThat(visit.hasAssignedVisit(new TreeSet<>(Set.of(3L, 4L)), 7L).assigned()).isTrue();
		assertThat(visit.hasAssignedVisit(Set.of(), 7L).assigned()).isFalse();
		verify(schedule).hasAssignedVisit(Set.of(), 7L);
		assertThat(visit.upcomingVisits(3L, NINE, NINE.plusDays(14)))
				.containsExactly(new VisitApi.ElderVisit(100L, 3L, 7L, NINE, "SCHEDULED", "BATHING"));
		assertThat(visit.findLatestCaregiverId(3L)).contains(7L);
		assertThat(visit.findLatestCaregiverId(4L)).isEmpty();
	}

	@Test
	void whetherACaregiverIsBusyAtATime() {
		when(lookups.caregiverBusy(7L, NINE, NINE.plusHours(1))).thenReturn(true);

		assertThat(visit.caregiverBusy(7L, NINE, NINE.plusHours(1)).busy()).isTrue();
		assertThat(visit.caregiverBusy(9L, NINE, NINE.plusHours(1)).busy()).isFalse();
	}

	@Test
	void aCallCarriesNoCsrfTokenAndLeavesNoSession() throws Exception {
		MvcResult result = mvc.perform(post("/internal/v1/visits/100/call-off")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"byUserId\":1,\"reason\":\"Family skipped it\"}"))
				.andExpect(status().isNoContent())
				.andReturn();

		assertThat(result.getRequest().getSession(false)).isNull();
		assertThat(result.getResponse().getCookies()).isEmpty();
	}

}
