package sg.nus.carelink.incident.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

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
 * Family details through real sessions, current bindings, MySQL and persistent auditing.
 *
 * @author Wang Zhili
 */
@SpringBootTest(properties = {"carelink.report.schedule-cron=-", "carelink.escalation.scan-initial-delay=PT1H"})
@AutoConfigureMockMvc
@Import(FamilyIncidentDetailIT.FixedTime.class)
class FamilyIncidentDetailIT {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 16, 0);
	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	private final JsonMapper json = JsonMapper.builder().build();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyIncidentDetailIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTime {
		@Bean
		@Primary
		Clock familyIncidentClock() {
			return Clock.fixed(Instant.parse("2026-10-07T08:00:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void prepare() {
		for (String table : List.of("audit_log", "notification", "incident_acknowledgement", "incident_log",
				"incident", "elder_family_binding", "elder", "family_member", "user_role", "app_user")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("""
				INSERT INTO app_user (id, username, password_hash, display_name) VALUES
				(7, 'family-a', '{noop}test-password', 'Family A'),
				(9, 'family-b', '{noop}test-password', 'Family B'),
				(10, 'manager', '{noop}test-password', 'Manager'),
				(12, 'no-profile', '{noop}test-password', 'Missing profile'),
				(13, 'no-binding', '{noop}test-password', 'No binding'),
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
				(42, 7, 'Family A'), (7, 9, 'Family B'), (55, 13, 'No binding')
				""");
		jdbc.update("INSERT INTO elder (id, full_name) VALUES (101, 'Elder A'), (102, 'Elder B'), (103, 'Elder C')");
		jdbc.update("""
				INSERT INTO elder_family_binding (elder_id, family_member_id, relationship, access_scope, status) VALUES
				(101, 42, 'DAUGHTER', 'FULL', 'ACTIVE'), (102, 42, 'SON', 'READ_ONLY', 'ACTIVE'),
				(101, 7, 'SON', 'FULL', 'ACTIVE'), (103, 7, 'SON', 'FULL', 'ACTIVE')
				""");
		seedIncident(601, 101, "ELDER_SOS", "SOS", "HIGH", "OPEN");
		seedIncident(602, 102, "CAREGIVER", "FALL", "LOW", "RESOLVED");
		seedIncident(603, 103, "SYSTEM_MISSED_CHECKIN", "SERVICE", "MEDIUM", "OPEN");
		jdbc.update("""
				INSERT INTO incident_acknowledgement
				(id, incident_id, family_member_id, viewed_at, acknowledged_at, response_note, created_at)
				VALUES (701, 601, 7, ?, ?, 'Other family private reply', ?)
				""", time(NOW.minusMinutes(2)), time(NOW.minusMinutes(1)), time(NOW.minusMinutes(2)));
		jdbc.update("""
				INSERT INTO incident_log (incident_id, actor, action, detail, occurred_at)
				VALUES (601, 'Manager internal identity', 'REPORTED', 'Internal handling note', ?)
				""", time(NOW));
	}

	@ParameterizedTest
	@ValueSource(longs = {601, 602})
	void returnsOnlyFamilyFieldsWithoutRequiringANotification(long id) throws Exception {
		JsonNode body = read(loginAs("family-a"), id);
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("id", "elderId", "visitId", "source", "category",
				"severity", "status", "description", "reportedAt", "resolvedAt", "acknowledgeBy", "acknowledgement");
		assertThat(body.path("id").longValue()).isEqualTo(id);
		assertThat(body.path("status").asString()).isEqualTo(id == 601 ? "OPEN" : "RESOLVED");
		assertThat(body.path("reportedAt").asString()).isEqualTo("2026-10-07T16:00:00+08:00");
		assertThat(body.path("resolvedAt").isNull()).isEqualTo(id == 601);
		assertThat(body.path("visitId").isNull()).isTrue();
		assertThat(body.path("acknowledgeBy").isNull()).isTrue();
		assertThat(body.path("acknowledgement").path("familyMemberId").longValue()).isEqualTo(42L);
		assertThat(body.path("acknowledgement").path("id").isNull()).isTrue();
		assertThat(body.path("acknowledgement").path("viewedAt").isNull()).isTrue();
		assertThat(body.path("acknowledgement").path("acknowledgedAt").isNull()).isTrue();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification", Long.class)).isZero();
	}

	@Test
	void returnsOnlyTheCurrentFamilyReceiptUsingProfileRatherThanAccountId() throws Exception {
		JsonNode receipt = read(loginAs("family-b"), 601).path("acknowledgement");
		assertThat(receipt.propertyNames()).containsExactlyInAnyOrder("id", "incidentId", "familyMemberId", "viewedAt",
				"acknowledgedAt", "responseNote");
		assertThat(receipt.path("id").longValue()).isEqualTo(701L);
		assertThat(receipt.path("familyMemberId").longValue()).isEqualTo(7L);
		assertThat(receipt.path("viewedAt").asString()).isEqualTo("2026-10-07T15:58:00+08:00");
		assertThat(receipt.path("acknowledgedAt").asString()).isEqualTo("2026-10-07T15:59:00+08:00");
		assertThat(receipt.path("responseNote").asString()).isEqualTo("Other family private reply");
		assertThat(read(loginAs("family-a"), 601).toString()).doesNotContain("Other family private reply");
	}

	@Test
	void getPreservesBusinessRowsAndNeverCreatesAReceipt() throws Exception {
		var incidents = jdbc.queryForList("SELECT * FROM incident ORDER BY id");
		var receipts = jdbc.queryForList("SELECT * FROM incident_acknowledgement ORDER BY id");
		var logs = jdbc.queryForList("SELECT * FROM incident_log ORDER BY id");
		var session = loginAs("family-a");
		read(session, 601);
		read(session, 601);
		assertThat(jdbc.queryForList("SELECT * FROM incident ORDER BY id")).isEqualTo(incidents);
		assertThat(jdbc.queryForList("SELECT * FROM incident_acknowledgement ORDER BY id")).isEqualTo(receipts);
		assertThat(jdbc.queryForList("SELECT * FROM incident_log ORDER BY id")).isEqualTo(logs);
	}

	@Test
	void accessComesFromTheActualIncidentElderNotClientParameters() throws Exception {
		mvc.perform(get("/api/family/incidents/603").session(loginAs("family-a"))
				.param("elderId", "101").param("familyMemberId", "7").param("projection", "manager"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.description").doesNotExist());
		assertThat(read(loginAs("family-b"), 603).path("source").asString()).isEqualTo("SYSTEM_MISSED_CHECKIN");
	}

	@ParameterizedTest
	@ValueSource(strings = {"REVOKED", "REJECTED", "PENDING_CONFIRMATION"})
	void bindingChangesTakeEffectInTheSameSession(String bindingStatus) throws Exception {
		var session = loginAs("family-a");
		read(session, 601);
		jdbc.update("UPDATE elder_family_binding SET status = ? WHERE elder_id = 101 AND family_member_id = 42", bindingStatus);
		mvc.perform(get("/api/family/incidents/601").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void readOnlyBindingExpiresAtTheExactSingaporeDeadline() throws Exception {
		var session = loginAs("family-a");
		jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 102", time(NOW.plusSeconds(1)));
		read(session, 602);
		jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 102", time(NOW));
		mvc.perform(get("/api/family/incidents/602").session(session)).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = {"no-profile", "no-binding", "caregiver", "elder", "manager"})
	void unsupportedAccountsCannotReadFamilyDetails(String username) throws Exception {
		mvc.perform(get("/api/family/incidents/601").session(loginAs(username))).andExpect(status().isForbidden());
	}

	@Test
	void anonymousDisabledAndRoleRevokedAccountsCannotReadDetails() throws Exception {
		mvc.perform(get("/api/family/incidents/601")).andExpect(status().isUnauthorized());
		var session = loginAs("family-a");
		jdbc.update("UPDATE app_user SET enabled = false WHERE id = 7");
		mvc.perform(get("/api/family/incidents/601").session(session)).andExpect(status().isForbidden());
		jdbc.update("UPDATE app_user SET enabled = true WHERE id = 7");
		jdbc.update("DELETE FROM user_role WHERE user_id = 7");
		mvc.perform(get("/api/family/incidents/601").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void unknownIncidentIs404AndMalformedIdIs400() throws Exception {
		var session = loginAs("family-a");
		mvc.perform(get("/api/family/incidents/999").session(session)).andExpect(status().isNotFound());
		mvc.perform(get("/api/family/incidents/not-a-number").session(session)).andExpect(status().isBadRequest());
	}

	@Test
	void managerKeepsExistingDetailAndFamilyCannotReadTheManagerProjection() throws Exception {
		var response = mvc.perform(get("/api/incidents/601").session(loginAs("manager")))
				.andExpect(status().isOk()).andReturn().getResponse();
		JsonNode manager = json.readTree(response.getContentAsString());
		assertThat(manager.path("incident").path("latitude").isNull()).isFalse();
		assertThat(manager.path("timeline").toString()).contains("Internal handling note");
		mvc.perform(get("/api/incidents/601").session(loginAs("family-a"))).andExpect(status().isForbidden());
	}

	@Test
	void readsAndFailuresAreAuditedWithoutIncidentContent() throws Exception {
		var session = loginAs("family-a");
		read(session, 601);
		mvc.perform(get("/api/family/incidents/603").session(session)).andExpect(status().isForbidden());
		mvc.perform(get("/api/family/incidents/999").session(session)).andExpect(status().isNotFound());
		var audit = jdbc.queryForList("SELECT * FROM audit_log WHERE resource_type = 'INCIDENT' ORDER BY id");
		assertThat(audit).extracting(row -> row.get("result")).containsExactly("OK", "DENIED", "FAILED");
		assertThat(audit).allSatisfy(row -> {
			assertThat(row.get("actor_user_id")).isEqualTo(7L);
			assertThat(row.get("action")).isEqualTo("READ");
			assertThat(row.get("detail")).isEqualTo("FM05_READ_INCIDENT");
			assertThat(row.get("occurred_at")).isNotNull();
		});
	}

	@Test
	void auditOutageDoesNotReturnCareContent() throws Exception {
		var session = loginAs("family-a");
		jdbc.execute("RENAME TABLE audit_log TO fm05_audit_unavailable");
		try {
			mvc.perform(get("/api/family/incidents/601").session(session)).andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.acknowledgement").doesNotExist());
		} finally {
			jdbc.execute("RENAME TABLE fm05_audit_unavailable TO audit_log");
		}
	}

	private void seedIncident(long id, long elderId, String source, String category, String severity, String incidentStatus) {
		jdbc.update("""
				INSERT INTO incident (id, elder_id, reported_by_user_id, responder_user_id, source, category,
				severity, status, latitude, longitude, location_text, description, reported_at, respond_by, resolved_at)
				VALUES (?, ?, 15, 10, ?, ?, ?, ?, 1.3000000, 103.8000000, 'Private location',
				'Care incident details', ?, ?, ?)
				""", id, elderId, source, category, severity, incidentStatus, time(NOW), time(NOW.plusMinutes(5)),
				incidentStatus.equals("RESOLVED") ? time(NOW.plusMinutes(1)) : null);
	}

	private static Timestamp time(LocalDateTime value) { return Timestamp.valueOf(value); }

	private JsonNode read(MockHttpSession session, long id) throws Exception {
		var response = mvc.perform(get("/api/family/incidents/{id}", id).session(session)).andExpect(status().isOk())
				.andReturn().getResponse();
		return json.readTree(response.getContentAsString());
	}

	private MockHttpSession loginAs(String username) throws Exception {
		var token = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		return (MockHttpSession) mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("username", username, "password", "test-password"))))
				.andExpect(status().isOk()).andReturn().getRequest().getSession(false);
	}
}
