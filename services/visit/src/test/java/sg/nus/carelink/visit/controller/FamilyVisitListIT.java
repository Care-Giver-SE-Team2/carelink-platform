package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.testsupport.SharedMySql;

/**
 * Verifies family schedule queries through login sessions and isolated MySQL data.
 *
 * @author Wang Zhili
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FamilyVisitListIT.FixedTime.class)
class FamilyVisitListIT {

	private static final String PATH = "/api/visits";
	private static final String PASSWORD = "test-password";
	private static final String SNAPSHOT = "2026-09-28T00:30:00+08:00";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 0, 30);

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyVisitListIT.class, "+05:00");
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	private final JsonMapper json = JsonMapper.builder().build();

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTime {
		@Bean
		@Primary
		Clock familyVisitListClock() {
			return Clock.fixed(Instant.parse("2026-09-27T16:30:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void prepareIsolatedVisits() {
		jdbc.update("DELETE FROM visit_assignment");
		jdbc.update("DELETE FROM visit");
		jdbc.update("DELETE FROM elder_family_binding");
		jdbc.update("DELETE FROM elder");
		jdbc.update("DELETE FROM family_member");
		jdbc.update("DELETE FROM caregiver");
		jdbc.update("DELETE FROM user_role");
		jdbc.update("DELETE FROM app_user");
		jdbc.update("""
				INSERT INTO app_user (id, username, password_hash, display_name) VALUES
				(7, 'family-a', '{noop}test-password', 'Family A'),
				(9, 'family-b', '{noop}test-password', 'Family B'),
				(10, 'manager', '{noop}test-password', 'Manager'),
				(12, 'no-profile', '{noop}test-password', 'Missing profile'),
				(13, 'no-active-binding', '{noop}test-password', 'Family without access'),
				(14, 'caregiver', '{noop}test-password', 'Caregiver'),
				(15, 'elder', '{noop}test-password', 'Elder')
				""");
		jdbc.update("""
				INSERT INTO user_role (user_id, role) VALUES
				(7, 'FAMILY'), (9, 'FAMILY'), (10, 'MANAGER'), (12, 'FAMILY'),
				(13, 'FAMILY'), (14, 'CAREGIVER'), (15, 'ELDER')
				""");
		jdbc.update("""
				INSERT INTO family_member (id, user_id, full_name) VALUES
				(42, 7, 'Family A'), (7, 9, 'Family B'), (55, 13, 'Family without access')
				""");
		for (long id : List.of(101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 110L)) {
			jdbc.update("INSERT INTO elder (id, full_name) VALUES (?, ?)", id, "Test elder " + id);
		}
		for (long id : List.of(201L, 202L, 203L, 204L, 205L)) {
			jdbc.update("INSERT INTO caregiver (id, user_id, full_name) VALUES (?, ?, ?)",
					id, id + 1000, "Test caregiver " + id);
		}
		seedBinding(101, 42, "FULL", "ACTIVE", null);
		seedBinding(102, 42, "READ_ONLY", "ACTIVE", NOW.plusSeconds(1));
		seedBinding(103, 42, "FULL", "ACTIVE", NOW);
		seedBinding(104, 42, "FULL", "ACTIVE", NOW.minusSeconds(1));
		seedBinding(105, 42, "FULL", "PENDING_CONFIRMATION", null);
		seedBinding(106, 42, "FULL", "REJECTED", null);
		seedBinding(107, 42, "FULL", "REVOKED", null);
		seedBinding(108, 55, "FULL", "PENDING_CONFIRMATION", null);
		seedBinding(110, 7, "FULL", "ACTIVE", null);
		seedVisit(301, 101, 201L, "2026-09-28T00:00:00", "SCHEDULED");
		seedVisit(302, 102, 202L, "2026-09-28T09:00:00", "COMPLETED");
		seedVisit(303, 101, null, "2026-09-28T09:00:00", "SCHEDULED");
		seedVisit(304, 101, 201L, "2026-10-04T23:59:59", "CANCELLED");
		seedVisit(305, 101, 201L, "2026-10-05T00:00:00", "SCHEDULED");
		seedVisit(306, 101, 203L, "2026-09-27T23:59:59", "COMPLETED");
		seedVisit(307, 102, 202L, "2026-09-29T09:00:00", "SCHEDULED");
		seedVisit(310, 110, 204L, "2026-09-28T00:00:00", "SCHEDULED");
		for (long elderId : List.of(103L, 104L, 105L, 106L, 107L, 108L)) {
			seedVisit(elderId + 300, elderId, 204L, "2026-09-28T00:00:00", "SCHEDULED");
		}
		jdbc.update("""
				UPDATE visit SET scheduled_end = '2026-09-28 10:00:00',
				checked_in_at = '2026-09-28 09:02:00', checked_out_at = '2026-09-28 10:01:00'
				WHERE id = 302
				""");
		jdbc.update("UPDATE visit SET service_type = NULL WHERE id = 303");
		jdbc.update("""
				INSERT INTO visit_assignment (visit_id, caregiver_id, status, assigned_at, ended_at)
				VALUES (301, 205, 'REPLACED', '2026-09-25 10:00:00', '2026-09-26 10:00:00')
				""");
	}

	@Test
	void defaultWeekUsesSingaporeDatesAndReturnsOnlyPermittedPublicFields() throws Exception {
		var body = readPage(loginAs("family-a"));
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("items", "page", "size", "totalElements");
		assertPage(body, 0, 20, 5, 301L, 302L, 303L, 307L, 304L);
		assertThat(body.path("items")).isNotEmpty().allSatisfy(item -> {
			assertThat(item.propertyNames()).containsExactlyInAnyOrder(
					"id", "elderId", "caregiverId", "serviceType", "scheduledStart", "scheduledEnd",
					"checkedInAt", "checkedOutAt", "status", "asOf");
			assertThat(item.path("asOf").asString()).isEqualTo(SNAPSHOT);
		});
		var completed = body.path("items").get(1);
		assertThat(completed.path("serviceType").asString()).isEqualTo("BATHING");
		assertThat(completed.path("scheduledStart").asString()).isEqualTo("2026-09-28T09:00:00+08:00");
		assertThat(completed.path("scheduledEnd").asString()).isEqualTo("2026-09-28T10:00:00+08:00");
		assertThat(completed.path("checkedInAt").asString()).isEqualTo("2026-09-28T09:02:00+08:00");
		assertThat(completed.path("checkedOutAt").asString()).isEqualTo("2026-09-28T10:01:00+08:00");
		var unassigned = body.path("items").get(2);
		for (String field : List.of("caregiverId", "serviceType", "scheduledEnd", "checkedInAt", "checkedOutAt")) {
			assertThat(unassigned.path(field).isNull()).as(field).isTrue();
		}
		assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+05:00");
		assertThat(jdbc.queryForObject("SELECT scheduled_start FROM visit WHERE id = 302", LocalDateTime.class))
				.isEqualTo(LocalDateTime.of(2026, 9, 28, 9, 0));
	}

	@Test
	void sessionIdentityCannotBeReplacedByFamilyOrRoleParameters() throws Exception {
		var body = readPage(loginAs("family-b"), "familyMemberId", "42", "userId", "7", "role", "MANAGER");
		assertPage(body, 0, 20, 1, 310L);
	}

	@Test
	void paginationUsesStableTimeThenIdOrderAndCountsOnlyReadableVisits() throws Exception {
		var session = loginAs("family-a");
		assertPage(readPage(session, "page", "0", "size", "2"), 0, 2, 5, 301L, 302L);
		assertPage(readPage(session, "page", "1", "size", "2"), 1, 2, 5, 303L, 307L);
		assertPage(readPage(session, "page", "2", "size", "2"), 2, 2, 5, 304L);
		assertPage(readPage(session, "page", "2147483647", "size", "200"), Integer.MAX_VALUE, 200, 5);
	}

	@Test
	void elderCaregiverAndStatusFiltersApplyBeforePaginationAndCounting() throws Exception {
		var session = loginAs("family-a");
		assertPage(readPage(session, "elderId", "102", "size", "1"), 0, 1, 2, 302L);
		assertPage(readPage(session, "caregiverId", "202", "status", "SCHEDULED"), 0, 20, 1, 307L);
		assertPage(readPage(session, "elderId", "101", "caregiverId", "201", "status", "CANCELLED"),
				0, 20, 1, 304L);
	}

	@Test
	void explicitDateRangeIncludesStartMidnightAndExcludesTheNextDaysMidnight() throws Exception {
		var session = loginAs("family-a");
		assertPage(readPage(session, "dateFrom", "2026-09-28", "dateTo", "2026-09-28"),
				0, 20, 3, 301L, 302L, 303L);
		assertPage(readPage(session, "dateFrom", "2026-10-04", "dateTo", "2026-10-04"), 0, 20, 1, 304L);
		assertPage(readPage(session, "dateFrom", "2026-09-27", "dateTo", "2026-09-27"), 0, 20, 1, 306L);
	}

	@Test
	void caregiverAccessUsesAllReadableEldersAndHistoryBeyondTheRequestedDates() throws Exception {
		var session = loginAs("family-a");
		assertPage(readPage(session, "elderId", "102", "caregiverId", "203", "status", "SCHEDULED"),
				0, 20, 0);
		assertPage(readPage(session, "elderId", "101", "caregiverId", "202"), 0, 20, 0);
		assertPage(readPage(session, "caregiverId", "203", "dateFrom", "2026-09-27", "dateTo", "2026-09-27"),
				0, 20, 1, 306L);
	}

	@ParameterizedTest
	@CsvSource({ "elderId,103", "elderId,104", "elderId,105", "elderId,106", "elderId,107",
			"elderId,108", "elderId,110", "elderId,999", "caregiverId,204", "caregiverId,205", "caregiverId,999" })
	void inaccessibleEldersAndUnrelatedOrReplacedOnlyCaregiversAreForbidden(String parameter, String value)
			throws Exception {
		mvc.perform(get(PATH).session(loginAs("family-a")).param(parameter, value))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void familyWithoutReadableBindingsGetsAnEmptyPageAndCannotSelectAnElder() throws Exception {
		var session = loginAs("no-active-binding");
		assertPage(readPage(session, "page", "2", "size", "3"), 2, 3, 0);
		mvc.perform(get(PATH).session(session).param("elderId", "108")).andExpect(status().isForbidden());
	}

	@Test
	void anonymousRequestsRequireLogin() throws Exception {
		mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "no-profile", "manager", "caregiver", "elder" })
	void accountsWithoutFamilyScheduleAccessAreForbidden(String username) throws Exception {
		mvc.perform(get(PATH).session(loginAs(username))).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = { "disabled", "role-removed" })
	void currentAccountAccessIsRecheckedForAnExistingSession(String change) throws Exception {
		var session = loginAs("family-a");
		if ("disabled".equals(change)) {
			jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = 7");
		} else {
			jdbc.update("DELETE FROM user_role WHERE user_id = 7 AND role = 'FAMILY'");
		}
		mvc.perform(get(PATH).session(session)).andExpect(status().isForbidden());
	}

	@Test
	void revokedBindingRemovesVisitsAndCaregiverAccessOnTheNextRequest() throws Exception {
		var session = loginAs("family-a");
		assertPage(readPage(session), 0, 20, 5, 301L, 302L, 303L, 307L, 304L);
		jdbc.update("UPDATE elder_family_binding SET status = 'REVOKED' WHERE elder_id = 101 AND family_member_id = 42");
		assertPage(readPage(session), 0, 20, 2, 302L, 307L);
		mvc.perform(get(PATH).session(session).param("elderId", "101")).andExpect(status().isForbidden());
		mvc.perform(get(PATH).session(session).param("caregiverId", "203")).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@CsvSource({ "SCHEDULED,4", "ARRIVED,1", "IN_PROGRESS,1", "COMPLETED,2", "VERIFIED,1",
			"AUTO_CLOSED,1", "EXCEPTION,1", "CANCELLED,2" })
	void eachVisitStateCanBeReturnedAndFiltered(String visitStatus, int expectedCount) throws Exception {
		seedVisit(601, 101, 201L, "2026-09-30T10:00:00", visitStatus);
		var body = readPage(loginAs("family-a"), "status", visitStatus);
		assertThat(body.path("totalElements").intValue()).isEqualTo(expectedCount);
		assertThat(body.path("items")).extracting(item -> item.path("id").longValue()).contains(601L);
		assertThat(body.path("items")).isNotEmpty()
				.allSatisfy(item -> assertThat(item.path("status").asString()).isEqualTo(visitStatus));
	}

	@ParameterizedTest
	@CsvSource({ "elderId,0", "elderId,-1", "elderId,abc", "caregiverId,0", "caregiverId,-1", "caregiverId,abc",
			"page,-1", "page,abc", "page,2147483648", "size,0", "size,201", "size,abc", "status,UNKNOWN",
			"dateFrom,2026-09-28", "dateTo,2026-10-04", "dateFrom,not-a-date", "dateTo,2026-02-30" })
	void invalidOrIncompleteQueryParametersReturnBadRequest(String parameter, String value) throws Exception {
		mvc.perform(get(PATH).session(loginAs("family-a")).param(parameter, value))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
	}

	@ParameterizedTest
	@CsvSource({ "2026-10-04,2026-09-28", "0999-12-31,1000-01-01", "9999-12-30,9999-12-31" })
	void reversedOrUnsupportedDateRangesReturnBadRequest(String dateFrom, String dateTo) throws Exception {
		mvc.perform(get(PATH).session(loginAs("family-a")).param("dateFrom", dateFrom).param("dateTo", dateTo))
				.andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = { "1000-01-01", "9999-12-30" })
	void supportedDateBoundariesCanReturnAnEmptyPage(String date) throws Exception {
		assertPage(readPage(loginAs("family-a"), "dateFrom", date, "dateTo", date), 0, 20, 0);
	}

	@Test
	void visitDetailKeepsInternalFieldsRestrictedToManagers() throws Exception {
		mvc.perform(get(PATH + "/301").session(loginAs("family-a")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(301))
				.andExpect(jsonPath("$.carePlanId").doesNotExist())
				.andExpect(jsonPath("$.stateDeadline").doesNotExist());
		mvc.perform(get(PATH + "/301").session(loginAs("manager")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(301))
				.andExpect(jsonPath("$.carePlanId").value(901));
	}

	private JsonNode readPage(MockHttpSession session, String... parameters) throws Exception {
		var request = get(PATH).session(session);
		for (int i = 0; i < parameters.length; i += 2) {
			request.param(parameters[i], parameters[i + 1]);
		}
		var response = mvc.perform(request).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn().getResponse();
		return json.readTree(response.getContentAsString());
	}

	private static void assertPage(JsonNode body, int page, int size, long total, Long... ids) {
		assertThat(body.path("page").intValue()).isEqualTo(page);
		assertThat(body.path("size").intValue()).isEqualTo(size);
		assertThat(body.path("totalElements").longValue()).isEqualTo(total);
		assertThat(body.path("items").isArray()).isTrue();
		assertThat(body.path("items")).extracting(item -> item.path("id").longValue()).containsExactly(ids);
	}

	private void seedVisit(long id, long elderId, Long caregiverId, String start, String visitStatus) {
		jdbc.update("""
				INSERT INTO visit
				(id, elder_id, caregiver_id, service_type, scheduled_start, status, care_plan_id, care_plan_node_id, state_deadline)
				VALUES (?, ?, ?, 'BATHING', ?, ?, 901, 902, '2026-09-28 09:30:00')
				""", id, elderId, caregiverId, LocalDateTime.parse(start), visitStatus);
	}

	private void seedBinding(long elderId, long familyId, String scope, String bindingStatus, LocalDateTime expiresAt) {
		jdbc.update("""
				INSERT INTO elder_family_binding
				(elder_id, family_member_id, relationship, access_scope, status, confirmed_at, expires_at)
				VALUES (?, ?, 'DAUGHTER', ?, ?, ?, ?)
				""", elderId, familyId, scope, bindingStatus, "ACTIVE".equals(bindingStatus) ? NOW.minusDays(1) : null, expiresAt);
	}

	private MockHttpSession loginAs(String username) throws Exception {
		Cookie token = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		var result = mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
				.andExpect(status().isOk()).andReturn();
		var session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session).isNotNull();
		return session;
	}
}
