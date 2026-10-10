package sg.nus.carelink.coreapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The client sends what core reads (paths, ISO dates, enum names, JSON bodies) and turns core's
 * error responses back into the exceptions the calls raised in-process.
 */
class CoreApiClientTest {

	private static final String CORE = "http://core/internal/v1";

	private final RestClient.Builder builder = RestClient.builder().baseUrl("http://core");

	private final MockRestServiceServer core = MockRestServiceServer.bindTo(builder).build();

	private final CoreApi api = CoreApiClients.create(builder);

	@AfterEach
	void everyExpectedCallWasMade() {
		core.verify();
	}

	@Test
	void readsAnAnswer() {
		core.expect(requestTo(CORE + "/family-access/ana/elders")).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("{\"elderIds\":[1,2]}", MediaType.APPLICATION_JSON));

		assertThat(api.readableElders("ana").elderIds()).isEqualTo(Set.of(1L, 2L));
	}

	@Test
	void sendsDatesInIsoAndEnumsByName() {
		core.expect(requestTo(CORE + "/caregivers/7/credential-alerts?today=2026-10-10"))
				.andRespond(withSuccess("{\"items\":[],\"context\":{\"asOfDate\":\"2026-10-10\",\"warningDays\":30,"
						+ "\"reviewRequired\":false}}", MediaType.APPLICATION_JSON));
		core.expect(requestTo(CORE + "/family-access/ana/elders/3?access=WRITE")).andRespond(withNoContent());

		assertThat(api.credentialAlerts(7L, LocalDate.of(2026, 10, 10)).context().warningDays()).isEqualTo(30);
		api.checkElderAccess("ana", 3L, CoreApi.Access.WRITE);
	}

	@Test
	void postsJsonBodies() {
		core.expect(requestTo(CORE + "/incidents/missed-check-ins")).andExpect(method(HttpMethod.POST))
				.andExpect(content().json("{\"elderId\":1,\"visitId\":2,\"dueAt\":\"2026-10-10T09:00:00\","
						+ "\"observedAt\":\"2026-10-10T09:20:00\"}"))
				.andRespond(withSuccess("{\"incidentId\":5}", MediaType.APPLICATION_JSON));

		CoreApi.IncidentRef incident = api.raiseMissedCheckIn(new CoreApi.MissedCheckInRequest(1L, 2L,
				LocalDateTime.of(2026, 10, 10, 9, 0), LocalDateTime.of(2026, 10, 10, 9, 20)));

		assertThat(incident.incidentId()).isEqualTo(5L);
	}

	@Test
	void aRefusalComesBackAsAccessDenied() {
		core.expect(requestTo(CORE + "/family-access/ana/elders/3?access=READ"))
				.andRespond(problem(HttpStatus.FORBIDDEN, "{\"detail\":\"A readable elder binding is required\"}"));

		assertThatThrownBy(() -> api.checkElderAccess("ana", 3L, CoreApi.Access.READ))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("A readable elder binding is required");
	}

	@Test
	void somethingMissingComesBackAsNotFoundOrAsEmpty() {
		core.expect(requestTo(CORE + "/elders/by-user/9"))
				.andRespond(problem(HttpStatus.NOT_FOUND, "{\"detail\":\"Elder for user [9] does not exist\"}"));
		core.expect(requestTo(CORE + "/caregivers/8/public-profile"))
				.andRespond(problem(HttpStatus.NOT_FOUND, "{\"detail\":\"Caregiver [8] does not exist\"}"));

		assertThatThrownBy(() -> api.elderByUser(9L)).isInstanceOf(CoreNotFound.class)
				.hasMessage("Elder for user [9] does not exist");
		assertThat(api.findCaregiverPublicProfile(8L)).isEmpty();
	}

	@Test
	void aBrokenRuleKeepsCoresCode() {
		core.expect(requestTo(CORE + "/care-plans/4/snapshot?elderId=1"))
				.andRespond(problem(HttpStatus.CONFLICT, "{\"code\":\"VISIT_PLAN_UNAVAILABLE\","
						+ "\"detail\":\"This visit does not reference a published plan for its elder.\"}"));

		assertThatThrownBy(() -> api.carePlanSnapshot(4L, 1L))
				.isInstanceOfSatisfying(CoreRuleViolation.class,
						violation -> assertThat(violation.code()).isEqualTo("VISIT_PLAN_UNAVAILABLE"));
	}

	private static org.springframework.test.web.client.ResponseCreator problem(HttpStatus status, String body) {
		return withStatus(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
	}

}
