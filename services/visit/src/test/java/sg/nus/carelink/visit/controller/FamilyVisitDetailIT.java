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
 * Family visit details through real login sessions, current bindings, MySQL and auditing.
 *
 * @author Wang Zhili
 */
@SpringBootTest(properties = {"carelink.report.schedule-cron=-", "carelink.escalation.scan-initial-delay=PT1H"})
@AutoConfigureMockMvc
@Import(FamilyVisitDetailIT.FixedTime.class)
class FamilyVisitDetailIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyVisitDetailIT.class, "+05:00");
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
		Clock familyVisitDetailClock() {
			return Clock.fixed(Instant.parse("2026-09-30T01:20:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void prepareIsolatedVisits() {
		jdbc.update("DELETE FROM audit_log");
		jdbc.update("DELETE FROM visit");
		jdbc.update("DELETE FROM caregiver");
		jdbc.update("DELETE FROM elder_family_binding");
		jdbc.update("DELETE FROM elder");
		jdbc.update("DELETE FROM family_member");
		jdbc.update("DELETE FROM user_role");
		jdbc.update("DELETE FROM app_user");
		jdbc.update("""
				INSERT INTO app_user (id, username, password_hash, display_name) VALUES
				(7, 'family-a', '{noop}test-password', 'Family A'),
				(9, 'family-b', '{noop}test-password', 'Family B'),
				(10, 'manager', '{noop}test-password', 'Manager'),
				(12, 'no-profile', '{noop}test-password', 'Missing profile'),
				(13, 'no-binding', '{noop}test-password', 'Family without access'),
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
		jdbc.update("INSERT INTO elder (id, full_name) VALUES (101, 'Elder A'), (102, 'Elder B'), (110, 'Elder C')");
		jdbc.update("INSERT INTO caregiver (id, user_id, full_name) VALUES (201, 14, 'Mei')");
		jdbc.update("""
				INSERT INTO elder_family_binding (elder_id, family_member_id, relationship, access_scope, status) VALUES
				(101, 42, 'DAUGHTER', 'FULL', 'ACTIVE'),
				(102, 42, 'DAUGHTER', 'READ_ONLY', 'ACTIVE'),
				(110, 7, 'DAUGHTER', 'FULL', 'ACTIVE')
				""");
		jdbc.update("""
				INSERT INTO visit (id, elder_id, caregiver_id, service_type, scheduled_start, scheduled_end,
				checked_in_at, status, state_deadline, version, created_at, updated_at) VALUES
				(501, 101, 201, 'Home care', '2026-09-30 09:00:00', '2026-09-30 10:00:00',
				'2026-09-30 09:03:00', 'IN_PROGRESS', '2026-09-30 10:15:00', 2,
				'2026-09-29 12:00:00', '2026-09-30 09:03:00'),
				(502, 102, NULL, NULL, '2026-09-29 23:30:00', NULL, NULL, 'SCHEDULED', NULL, 0,
				'2026-09-28 12:00:00', '2026-09-28 12:00:00'),
				(510, 110, 201, 'Home care', '2026-09-30 10:00:00', NULL, NULL, 'SCHEDULED', NULL, 0,
				'2026-09-29 12:00:00', '2026-09-29 12:00:00')
				""");
	}

	@Test
	void readsOnlyFamilyFieldsWithActualStateAndSingaporeTimes() throws Exception {
		assertThat(readDetail(loginAs("family-a"), 501)).isEqualTo(json.readTree("""
				{
				  "id": 501, "elderId": 101, "caregiverId": 201, "serviceType": "Home care",
				  "scheduledStart": "2026-09-30T09:00:00+08:00",
				  "scheduledEnd": "2026-09-30T10:00:00+08:00",
				  "checkedInAt": "2026-09-30T09:03:00+08:00", "checkedOutAt": null,
				  "status": "IN_PROGRESS", "asOf": "2026-09-30T09:20:00+08:00"
				}
				"""));
	}

	@Test
	void readOnlyBindingCanReadPastScheduledVisitsWithoutInventingActualTimes() throws Exception {
		assertThat(readDetail(loginAs("family-a"), 502)).isEqualTo(json.readTree("""
				{
				  "id": 502, "elderId": 102, "caregiverId": null, "serviceType": null,
				  "scheduledStart": "2026-09-29T23:30:00+08:00", "scheduledEnd": null,
				  "checkedInAt": null, "checkedOutAt": null, "status": "SCHEDULED",
				  "asOf": "2026-09-30T09:20:00+08:00"
				}
				"""));
	}

	@ParameterizedTest
	@ValueSource(strings = {"SCHEDULED", "ARRIVED", "IN_PROGRESS", "COMPLETED", "VERIFIED", "AUTO_CLOSED", "EXCEPTION", "CANCELLED"})
	void readsTheStoredStateWithoutAdvancingIt(String state) throws Exception {
		jdbc.update("UPDATE visit SET status = ? WHERE id = 501", state);
		assertThat(readDetail(loginAs("family-a"), 501).path("status").asString()).isEqualTo(state);
	}

	@Test
	void subsequentReadReturnsNewlyStoredCheckoutFactsWithExplicitSingaporeOffset() throws Exception {
		var session = loginAs("family-a");
		readDetail(session, 502);
		jdbc.update("""
				UPDATE visit SET checked_in_at = '2026-09-29 23:35:00', checked_out_at = '2026-09-30 00:20:00',
				status = 'COMPLETED' WHERE id = 502
				""");
		var body = readDetail(session, 502);
		assertThat(body.path("status").asString()).isEqualTo("COMPLETED");
		assertThat(body.path("checkedInAt").asString()).isEqualTo("2026-09-29T23:35:00+08:00");
		assertThat(body.path("checkedOutAt").asString()).isEqualTo("2026-09-30T00:20:00+08:00");
		assertThat(body.path("asOf").asString()).isEqualTo("2026-09-30T09:20:00+08:00");
		assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+05:00");
	}

	@Test
	void eachSessionUsesItsOwnFamilyProfileAndCannotSpoofResourceOwnership() throws Exception {
		var familyB = loginAs("family-b");
		assertThat(readDetail(familyB, 510).path("elderId").longValue()).isEqualTo(110L);
		mvc.perform(get("/api/visits/501").session(familyB)
				.param("elderId", "110").param("familyMemberId", "42").param("userId", "7")
				.param("role", "MANAGER").param("projection", "Visit"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.scheduledStart").doesNotExist());
		mvc.perform(get("/api/visits/510").session(loginAs("family-a")))
				.andExpect(status().isForbidden());
	}

	@Test
	void requestingManagerProjectionDoesNotExposeInternalVisitFields() throws Exception {
		var session = loginAs("family-a");
		var expected = readDetail(session, 501);
		var response = mvc.perform(get("/api/visits/501").session(session)
				.param("role", "MANAGER").param("projection", "Visit"))
				.andExpect(status().isOk()).andReturn().getResponse();
		assertThat(json.readTree(response.getContentAsString())).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = {"REVOKED", "REJECTED", "PENDING_CONFIRMATION"})
	void bindingStatusIsRecheckedInTheSameSession(String bindingStatus) throws Exception {
		var session = loginAs("family-a");
		readDetail(session, 501);
		jdbc.update("UPDATE elder_family_binding SET status = ? WHERE elder_id = 101", bindingStatus);
		mvc.perform(get("/api/visits/501").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void readOnlyBindingExpiresAtTheExactSingaporeTime() throws Exception {
		var session = loginAs("family-a");
		jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 102",
				LocalDateTime.of(2026, 9, 30, 9, 20, 1));
		readDetail(session, 502);
		jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 102",
				LocalDateTime.of(2026, 9, 30, 9, 20));
		mvc.perform(get("/api/visits/502").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void changedVisitOwnershipRequiresAccessToItsCurrentElder() throws Exception {
		var session = loginAs("family-a");
		readDetail(session, 501);
		jdbc.update("UPDATE visit SET elder_id = 110 WHERE id = 501");
		mvc.perform(get("/api/visits/501").session(session)).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = {"no-profile", "no-binding", "caregiver", "elder"})
	void unavailableFamilyAccessAndOtherRolesCannotReadDetails(String username) throws Exception {
		mvc.perform(get("/api/visits/501").session(loginAs(username)))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.scheduledStart").doesNotExist());
	}

	@Test
	void disabledAccountCannotContinueReadingWithItsOldSession() throws Exception {
		var session = loginAs("family-a");
		readDetail(session, 501);
		jdbc.update("UPDATE app_user SET enabled = false WHERE id = 7");
		mvc.perform(get("/api/visits/501").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void removedFamilyRoleCannotContinueReadingWithItsOldSession() throws Exception {
		var session = loginAs("family-a");
		readDetail(session, 501);
		jdbc.update("DELETE FROM user_role WHERE user_id = 7 AND role = 'FAMILY'");
		mvc.perform(get("/api/visits/501").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void anonymousRequestRequiresLogin() throws Exception {
		mvc.perform(get("/api/visits/501")).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(longs = {999, 0, -1})
	void absentVisitIs404RatherThanAnEmptyOrForbiddenResponse(long id) throws Exception {
		mvc.perform(get("/api/visits/{id}", id).session(loginAs("family-a")))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404));
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "9223372036854775808"})
	void malformedVisitIdsReturn400(String id) throws Exception {
		mvc.perform(get("/api/visits/{id}", id).session(loginAs("family-a")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void managerRetainsOriginalVisitResponseAndMissingRecordBehavior() throws Exception {
		var session = loginAs("manager");
		var body = readDetail(session, 501);
		assertThat(body.propertyNames()).containsExactlyInAnyOrder(
				"id", "elderId", "caregiverId", "carePlanNodeId", "absenceId", "serviceType", "scheduledStart",
				"scheduledEnd", "checkedInAt", "checkedOutAt", "status", "stateDeadline", "carePlanId", "version",
				"createdAt", "updatedAt");
		assertThat(body.path("stateDeadline").asString()).isEqualTo("2026-09-30T10:15:00");
		assertThat(body.path("scheduledStart").asString()).isEqualTo("2026-09-30T09:00:00");
		assertThat(body.path("version").intValue()).isEqualTo(2);
		assertThat(readDetail(session, 510).path("elderId").longValue()).isEqualTo(110L);
		mvc.perform(get("/api/visits/999").session(session)).andExpect(status().isNotFound());
	}

	@Test
	void familyDetailsDoNotGrantAccessToTheManagerRoster() throws Exception {
		mvc.perform(get("/api/visits/roster").session(loginAs("family-a")))
				.andExpect(status().isForbidden());
	}

	@Test
	void readLeavesCareFactsUnchangedAndRecordsOnlyAccessMetadata() throws Exception {
		var before = jdbc.queryForList("SELECT * FROM visit ORDER BY id");
		var session = loginAs("family-a");
		readDetail(session, 501);
		mvc.perform(get("/api/visits/510").session(session)).andExpect(status().isForbidden());
		mvc.perform(get("/api/visits/999").session(session)).andExpect(status().isNotFound());
		assertThat(jdbc.queryForList("SELECT * FROM visit ORDER BY id")).isEqualTo(before);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_task", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_state_transition", Long.class)).isZero();
		var entries = jdbc.queryForList("SELECT * FROM audit_log WHERE resource_type = 'VISIT' ORDER BY id");
		assertThat(entries).extracting(entry -> entry.get("result")).containsExactly("OK", "DENIED", "FAILED");
		assertThat(entries).extracting(entry -> entry.get("resource_id")).containsExactly(501L, 510L, 999L);
		assertThat(entries).allSatisfy(entry -> {
			assertThat(entry).containsEntry("actor_user_id", 7L).containsEntry("action", "READ")
					.containsEntry("detail", "FM03_READ_VISIT")
					.containsEntry("occurred_at", LocalDateTime.of(2026, 9, 30, 9, 20));
		});
	}

	@Test
	void auditOutageReturns503WithoutVisitContent() throws Exception {
		var session = loginAs("family-a");
		jdbc.execute("RENAME TABLE audit_log TO fm03_detail_audit_unavailable");
		try {
			for (long id : List.of(501L, 510L, 999L)) {
				mvc.perform(get("/api/visits/{id}", id).session(session))
						.andExpect(status().isServiceUnavailable())
						.andExpect(jsonPath("$.scheduledStart").doesNotExist());
			}
		} finally {
			jdbc.execute("RENAME TABLE fm03_detail_audit_unavailable TO audit_log");
		}
	}

	private JsonNode readDetail(MockHttpSession session, long id) throws Exception {
		var response = mvc.perform(get("/api/visits/{id}", id).session(session)).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn().getResponse();
		return json.readTree(response.getContentAsString());
	}

	private MockHttpSession loginAs(String username) throws Exception {
		Cookie token = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		var result = mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("username", username, "password", "test-password"))))
				.andExpect(status().isOk()).andReturn();
		var session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session).isNotNull();
		return session;
	}
}
