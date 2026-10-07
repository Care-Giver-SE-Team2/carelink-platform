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
 * Family task progress through real login sessions, current bindings, MySQL and auditing.
 *
 * @author Wang Zhili
 */
@SpringBootTest(properties = {"carelink.report.schedule-cron=-", "carelink.escalation.scan-initial-delay=PT1H"})
@AutoConfigureMockMvc
@Import(FamilyVisitTaskIT.FixedTime.class)
class FamilyVisitTaskIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyVisitTaskIT.class, "+05:00");
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
		Clock familyVisitTaskClock() {
			return Clock.fixed(Instant.parse("2026-09-30T01:20:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void prepareIsolatedTasks() {
		jdbc.update("DELETE FROM audit_log");
		jdbc.update("DELETE FROM visit_task");
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
		jdbc.update("""
				INSERT INTO visit_task (id, visit_id, care_plan_node_id, name, status, outcome, caregiver_note, completed_at) VALUES
				(904, 501, 804, 'Optional activity', 'REFUSED', 'Internal refusal outcome', 'Private refusal note', NULL),
				(901, 501, 801, 'Assist with walking', 'DONE', 'Internal done outcome', 'Private done note', '2026-09-30 09:15:00'),
				(903, 501, 803, 'Exercise', 'SKIPPED', 'Internal skipped outcome', 'Private skipped note', NULL),
				(902, 501, 802, 'Meal preparation', 'PENDING', 'Internal pending outcome', 'Private pending note', NULL),
				(910, 510, NULL, 'Another family task', 'DONE', 'Other outcome', 'Other note', '2026-09-30 10:15:00')
				""");
	}

	@Test
	void exposesOnlyFamilyTaskFieldsWithAllStoredStatesInIdOrder() throws Exception {
		assertThat(readTasks(loginAs("family-a"), 501)).isEqualTo(json.readTree("""
				[
				  {"id":901,"visitId":501,"name":"Assist with walking","status":"DONE",
				   "completedAt":"2026-09-30T09:15:00+08:00"},
				  {"id":902,"visitId":501,"name":"Meal preparation","status":"PENDING","completedAt":null},
				  {"id":903,"visitId":501,"name":"Exercise","status":"SKIPPED","completedAt":null},
				  {"id":904,"visitId":501,"name":"Optional activity","status":"REFUSED","completedAt":null}
				]
				"""));
	}

	@Test
	void fullAndReadOnlyBindingsReceiveTheSameFamilyFields() throws Exception {
		var session = loginAs("family-a");
		var full = readTasks(session, 501);
		jdbc.update("UPDATE elder_family_binding SET access_scope = 'READ_ONLY' WHERE elder_id = 101");
		assertThat(readTasks(session, 501)).isEqualTo(full);
	}

	@Test
	void emptyTaskListDoesNotInventTasksForAnInProgressVisit() throws Exception {
		jdbc.update("DELETE FROM visit_task WHERE visit_id = 501");
		assertThat(readTasks(loginAs("family-a"), 501)).isEqualTo(json.readTree("[]"));
	}

	@Test
	void emptyTaskListStillRequiresAccessToItsVisit() throws Exception {
		jdbc.update("DELETE FROM visit_task WHERE visit_id = 510");
		mvc.perform(get("/api/visits/510/tasks").session(loginAs("family-a"))).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = {"PENDING", "DONE", "SKIPPED", "REFUSED"})
	void storedCompletionTimeDoesNotChangeTaskState(String state) throws Exception {
		jdbc.update("UPDATE visit_task SET status = ? WHERE id = 901", state);
		var task = readTasks(loginAs("family-a"), 501).get(0);
		assertThat(task.path("status").asString()).isEqualTo(state);
		assertThat(task.path("completedAt").asString()).isEqualTo("2026-09-30T09:15:00+08:00");
	}

	@Test
	void completedVisitDoesNotFillMissingTaskTimesOrAdvancePendingTasks() throws Exception {
		jdbc.update("UPDATE visit SET status = 'COMPLETED', checked_out_at = '2026-09-30 10:00:00' WHERE id = 501");
		jdbc.update("UPDATE visit_task SET completed_at = NULL WHERE id = 901");
		var tasks = readTasks(loginAs("family-a"), 501);
		assertThat(tasks.get(0)).isEqualTo(json.readTree("""
				{"id":901,"visitId":501,"name":"Assist with walking","status":"DONE","completedAt":null}
				"""));
		assertThat(tasks.get(1)).isEqualTo(json.readTree("""
				{"id":902,"visitId":501,"name":"Meal preparation","status":"PENDING","completedAt":null}
				"""));
	}

	@Test
	void subsequentReadSeesStoredTaskChangesWithSingaporeOffset() throws Exception {
		var session = loginAs("family-a");
		assertThat(readTasks(session, 501).get(1).path("status").asString()).isEqualTo("PENDING");
		jdbc.update("UPDATE visit_task SET status = 'DONE', completed_at = '2026-10-01 00:20:00' WHERE id = 902");
		assertThat(readTasks(session, 501).get(1)).isEqualTo(json.readTree("""
				{"id":902,"visitId":501,"name":"Meal preparation","status":"DONE",
				 "completedAt":"2026-10-01T00:20:00+08:00"}
				"""));
		assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+05:00");
	}

	@Test
	void eachSessionUsesItsOwnFamilyProfileAndCannotSpoofResourceOwnership() throws Exception {
		var familyB = loginAs("family-b");
		assertThat(readTasks(familyB, 510).get(0).path("visitId").longValue()).isEqualTo(510L);
		mvc.perform(get("/api/visits/501/tasks").session(familyB)
				.param("elderId", "110").param("familyMemberId", "42").param("userId", "7")
				.param("role", "MANAGER").param("projection", "VisitTask"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.caregiverNote").doesNotExist());
		mvc.perform(get("/api/visits/510/tasks").session(loginAs("family-a")))
				.andExpect(status().isForbidden());
	}

	@Test
	void requestingCaregiverProjectionDoesNotExposeInternalTaskFields() throws Exception {
		var session = loginAs("family-a");
		var expected = readTasks(session, 501);
		var response = mvc.perform(get("/api/visits/501/tasks").session(session)
				.param("role", "CAREGIVER").param("projection", "VisitTask"))
				.andExpect(status().isOk()).andReturn().getResponse();
		assertThat(json.readTree(response.getContentAsString())).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = {"REVOKED", "REJECTED", "PENDING_CONFIRMATION"})
	void bindingStatusIsRecheckedInTheSameSession(String bindingStatus) throws Exception {
		var session = loginAs("family-a");
		readTasks(session, 501);
		jdbc.update("UPDATE elder_family_binding SET status = ? WHERE elder_id = 101", bindingStatus);
		mvc.perform(get("/api/visits/501/tasks").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void readOnlyBindingExpiresAtTheExactSingaporeTime() throws Exception {
		var session = loginAs("family-a");
		jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 102",
				LocalDateTime.of(2026, 9, 30, 9, 20, 1));
		readTasks(session, 502);
		jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 102",
				LocalDateTime.of(2026, 9, 30, 9, 20));
		mvc.perform(get("/api/visits/502/tasks").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void changedVisitOwnershipRequiresAccessToItsCurrentElder() throws Exception {
		var session = loginAs("family-a");
		readTasks(session, 501);
		jdbc.update("UPDATE visit SET elder_id = 110 WHERE id = 501");
		mvc.perform(get("/api/visits/501/tasks").session(session)).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = {"no-profile", "no-binding", "manager", "caregiver", "elder"})
	void unavailableFamilyAccessAndOtherRolesCannotReadTask(String username) throws Exception {
		mvc.perform(get("/api/visits/501/tasks").session(loginAs(username)))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.caregiverNote").doesNotExist());
	}

	@Test
	void disabledAccountCannotContinueReadingWithItsOldSession() throws Exception {
		var session = loginAs("family-a");
		readTasks(session, 501);
		jdbc.update("UPDATE app_user SET enabled = false WHERE id = 7");
		mvc.perform(get("/api/visits/501/tasks").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void removedFamilyRoleCannotContinueReadingWithItsOldSession() throws Exception {
		var session = loginAs("family-a");
		readTasks(session, 501);
		jdbc.update("DELETE FROM user_role WHERE user_id = 7 AND role = 'FAMILY'");
		mvc.perform(get("/api/visits/501/tasks").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void anonymousRequestRequiresLogin() throws Exception {
		mvc.perform(get("/api/visits/501/tasks")).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(longs = {999, 0, -1})
	void absentVisitIs404RatherThanAnEmptyOrForbiddenResponse(long id) throws Exception {
		mvc.perform(get("/api/visits/{id}/tasks", id).session(loginAs("family-a")))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404));
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "9223372036854775808"})
	void malformedVisitIdsReturn400(String id) throws Exception {
		mvc.perform(get("/api/visits/{id}/tasks", id).session(loginAs("family-a")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void readPreservesCareFactsAndRecordsOnlyAccessMetadata() throws Exception {
		var visits = jdbc.queryForList("SELECT * FROM visit ORDER BY id");
		var tasks = jdbc.queryForList("SELECT * FROM visit_task ORDER BY id");
		var session = loginAs("family-a");
		readTasks(session, 501);
		readTasks(session, 502);
		mvc.perform(get("/api/visits/510/tasks").session(session)).andExpect(status().isForbidden());
		mvc.perform(get("/api/visits/999/tasks").session(session)).andExpect(status().isNotFound());
		assertThat(jdbc.queryForList("SELECT * FROM visit ORDER BY id")).isEqualTo(visits);
		assertThat(jdbc.queryForList("SELECT * FROM visit_task ORDER BY id")).isEqualTo(tasks);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM visit_state_transition", Long.class)).isZero();
		var entries = jdbc.queryForList("SELECT * FROM audit_log WHERE resource_type = 'VISIT' ORDER BY id");
		assertThat(entries).extracting(entry -> entry.get("result")).containsExactly("OK", "OK", "DENIED", "FAILED");
		assertThat(entries).extracting(entry -> entry.get("resource_id")).containsExactly(501L, 502L, 510L, 999L);
		assertThat(entries).allSatisfy(entry -> {
			assertThat(entry).containsEntry("actor_user_id", 7L).containsEntry("action", "READ")
					.containsEntry("detail", "FM03_READ_VISIT_TASKS")
					.containsEntry("occurred_at", LocalDateTime.of(2026, 9, 30, 9, 20));
		});
	}

	@Test
	void auditOutageReturns503WithoutTaskContent() throws Exception {
		var session = loginAs("family-a");
		jdbc.execute("RENAME TABLE audit_log TO fm03_tasks_audit_unavailable");
		try {
			for (long id : List.of(501L, 502L, 510L, 999L)) {
				var response = mvc.perform(get("/api/visits/{id}/tasks", id).session(session))
						.andExpect(status().isServiceUnavailable())
						.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
						.andReturn().getResponse().getContentAsString();
				assertThat(response).doesNotContain("Assist with walking", "Private done note", "Internal done outcome");
			}
		} finally {
			jdbc.execute("RENAME TABLE fm03_tasks_audit_unavailable TO audit_log");
		}
	}

	private JsonNode readTasks(MockHttpSession session, long id) throws Exception {
		var response = mvc.perform(get("/api/visits/{id}/tasks", id).session(session)).andExpect(status().isOk())
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
