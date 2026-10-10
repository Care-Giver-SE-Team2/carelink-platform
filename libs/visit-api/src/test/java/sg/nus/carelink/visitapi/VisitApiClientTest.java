package sg.nus.carelink.visitapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * The client sends what visit reads (paths, ISO date-times, one parameter per elder, JSON bodies)
 * and turns visit's error responses back into the exceptions the calls raised in-process.
 */
class VisitApiClientTest {

	private static final String VISIT = "http://visit/internal/v1";

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 12, 9, 0);

	private final RestClient.Builder builder = RestClient.builder().baseUrl("http://visit");

	private final MockRestServiceServer visit = MockRestServiceServer.bindTo(builder).build();

	private final VisitApi api = VisitApiClients.create(builder);

	@AfterEach
	void everyExpectedCallWasMade() {
		visit.verify();
	}

	@Test
	void readsAVisit() {
		visit.expect(requestTo(VISIT + "/visits/5")).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("{\"visitId\":5,\"elderId\":1,\"caregiverId\":7,\"carePlanId\":3,"
						+ "\"serviceType\":\"BATHING\",\"start\":\"2026-10-12T09:00:00\",\"end\":\"2026-10-12T10:00:00\","
						+ "\"status\":\"SCHEDULED\",\"absenceId\":null}", MediaType.APPLICATION_JSON));

		assertThat(api.visit(5L)).isEqualTo(new VisitApi.VisitSlot(5L, 1L, 7L, 3L, "BATHING", NINE,
				NINE.plusHours(1), "SCHEDULED", null));
	}

	@Test
	void sendsDateTimesInIsoAndEachElderAsAParameterOfItsOwn() {
		visit.expect(requestTo(VISIT + "/visits/unstarted?caregiverId=7&from=2026-10-12T09%3A00%3A00"
				+ "&until=2026-10-13T09%3A00%3A00")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
		visit.expect(requestTo(VISIT + "/visits/assigned?elderIds=1&elderIds=2&caregiverId=7"))
				.andRespond(withSuccess("{\"assigned\":true}", MediaType.APPLICATION_JSON));
		visit.expect(requestTo(VISIT + "/visits/finished-with?elderId=1&since=2026-09-12T09%3A00%3A00"
				+ "&until=2026-10-12T09%3A00%3A00")).andRespond(withSuccess("{\"7\":3}", MediaType.APPLICATION_JSON));

		assertThat(api.unstartedFor(7L, NINE, NINE.plusDays(1))).isEmpty();
		assertThat(api.hasAssignedVisit(new TreeSet<>(List.of(2L, 1L)), 7L).assigned()).isTrue();
		assertThat(api.finishedVisitsWith(1L, NINE.minusMonths(1), NINE)).isEqualTo(Map.of(7L, 3));
	}

	@Test
	void postsJsonBodies() {
		visit.expect(requestTo(VISIT + "/visits/5/reassignment")).andExpect(method(HttpMethod.POST))
				.andExpect(content().json("{\"toCaregiverId\":8,\"why\":{\"absenceId\":4,\"byUserId\":10,"
						+ "\"rosteringCandidateId\":null,\"reason\":\"Annual leave\"}}"))
				.andRespond(withSuccess());
		visit.expect(requestTo(VISIT + "/visits/5/move")).andExpect(method(HttpMethod.POST))
				.andExpect(content().json("{\"newStart\":\"2026-10-12T09:00:00\",\"toCaregiverId\":8}"))
				.andRespond(withSuccess("{\"visitId\":6}", MediaType.APPLICATION_JSON));
		visit.expect(requestTo(VISIT + "/visits/schedule")).andExpect(method(HttpMethod.POST))
				.andExpect(content().json("[{\"elderId\":1,\"caregiverId\":null,\"carePlanId\":3,\"carePlanNodeId\":11,"
						+ "\"serviceType\":\"BATHING\",\"start\":\"2026-10-12T09:00:00\",\"end\":\"2026-10-12T10:00:00\"}]"))
				.andRespond(withSuccess("{\"created\":1,\"covered\":0}", MediaType.APPLICATION_JSON));

		VisitApi.Change why = new VisitApi.Change(4L, 10L, null, "Annual leave");
		api.reassign(5L, new VisitApi.Reassignment(8L, why));
		assertThat(api.moveTo(5L, new VisitApi.Move(NINE, 8L, why)).visitId()).isEqualTo(6L);
		assertThat(api.schedule(List.of(new VisitApi.PlannedVisit(1L, null, 3L, 11L, "BATHING", NINE,
				NINE.plusHours(1))))).isEqualTo(new VisitApi.Outcome(1, 0));
	}

	@Test
	void aRefusalComesBackAsAccessDenied() {
		visit.expect(requestTo(VISIT + "/visits/5/state"))
				.andRespond(problem(HttpStatus.FORBIDDEN, "{\"detail\":\"Not yours\"}"));

		assertThatThrownBy(() -> api.visitState(5L)).isInstanceOf(AccessDeniedException.class).hasMessage("Not yours");
	}

	@Test
	void somethingMissingComesBackAsNotFoundOrAsEmpty() {
		visit.expect(requestTo(VISIT + "/visits/9"))
				.andRespond(problem(HttpStatus.NOT_FOUND, "{\"detail\":\"Visit [9] does not exist\"}"));
		visit.expect(requestTo(VISIT + "/visits/9"))
				.andRespond(problem(HttpStatus.NOT_FOUND, "{\"detail\":\"Visit [9] does not exist\"}"));
		visit.expect(requestTo(VISIT + "/visits/9/state")).andRespond(withStatus(HttpStatus.NOT_FOUND));
		visit.expect(requestTo(VISIT + "/visits/latest-caregiver?elderId=1"))
				.andRespond(problem(HttpStatus.NOT_FOUND, "{\"detail\":\"Elder [1] has had no visit\"}"));

		assertThatThrownBy(() -> api.visit(9L)).isInstanceOf(VisitNotFound.class)
				.hasMessage("Visit [9] does not exist");
		assertThat(api.findVisit(9L)).isEmpty();
		assertThat(api.findVisitState(9L)).isEmpty();
		assertThat(api.findLatestCaregiverId(1L)).isEmpty();
	}

	@Test
	void whatIsThereComesBackPresent() {
		visit.expect(requestTo(VISIT + "/visits/5/state"))
				.andRespond(withSuccess("{\"visitId\":5,\"caregiverId\":7,\"status\":\"SCHEDULED\",\"checkedIn\":false}",
						MediaType.APPLICATION_JSON));
		visit.expect(requestTo(VISIT + "/visits/latest-caregiver?elderId=1"))
				.andRespond(withSuccess("{\"caregiverId\":7}", MediaType.APPLICATION_JSON));
		visit.expect(requestTo(VISIT + "/visits/5"))
				.andRespond(withSuccess("{\"visitId\":5}", MediaType.APPLICATION_JSON));

		assertThat(api.findVisitState(5L)).contains(new VisitApi.State(5L, 7L, "SCHEDULED", false));
		assertThat(api.findLatestCaregiverId(1L)).contains(7L);
		assertThat(api.findVisit(5L)).map(VisitApi.VisitSlot::visitId).contains(5L);
	}

	@Test
	void aBrokenRuleKeepsVisitsCode() {
		visit.expect(requestTo(VISIT + "/visits/5/call-off"))
				.andRespond(problem(HttpStatus.CONFLICT, "{\"code\":\"VISIT_ALREADY_STARTED\","
						+ "\"detail\":\"The visit has started.\"}"));
		visit.expect(requestTo(VISIT + "/visits/5/uncovered"))
				.andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.TEXT_PLAIN).body("not json"));

		VisitApi.Change why = new VisitApi.Change(null, 10L, null, "Family skipped it");
		assertThatThrownBy(() -> api.callOff(5L, why)).isInstanceOfSatisfying(VisitRuleViolation.class,
				violation -> assertThat(violation.code()).isEqualTo("VISIT_ALREADY_STARTED"));
		assertThatThrownBy(() -> api.markUncovered(5L, why)).isInstanceOfSatisfying(VisitRuleViolation.class,
				violation -> assertThat(violation).hasMessage("POST /internal/v1/visits/5/uncovered")
						.extracting(VisitRuleViolation::code).isEqualTo("UNKNOWN"));
	}

	@Test
	void anythingElseKeepsItsStatus() {
		visit.expect(requestTo(VISIT + "/visits/5/uncovered-exception"))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(() -> api.markUncoveredAsException(5L)).isInstanceOfSatisfying(
				RestClientResponseException.class,
				failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
	}

	private static ResponseCreator problem(HttpStatus status, String body) {
		return withStatus(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
	}

}
