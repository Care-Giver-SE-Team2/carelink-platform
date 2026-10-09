package sg.nus.carelink.incident.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.shared.audit.persistence.repository.AuditLogJpaRepository;

/** FM05 writes through real sessions, CSRF, concurrent transactions and MySQL. @author Wang Zhili */
@SpringBootTest(properties = {"carelink.report.schedule-cron=-", "carelink.escalation.scan-initial-delay=PT1H"})
@AutoConfigureMockMvc
@Import(FamilyIncidentReceiptIT.TimeConfiguration.class)
class FamilyIncidentReceiptIT {

	private static final Instant START = Instant.parse("2026-10-07T08:00:00Z");
	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private TestClock clock;
	@Autowired private AuditLogJpaRepository auditRows;
	private final JsonMapper json = JsonMapper.builder().build();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyIncidentReceiptIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TimeConfiguration {
		@Bean @Primary TestClock receiptClock() { return new TestClock(); }
	}

	static class TestClock extends Clock {
		volatile Instant now = START;
		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
		@Override public Instant instant() { return now; }
	}

	@BeforeEach
	void prepare() {
		clock.now = START;
		for (String table : List.of("audit_log", "notification", "incident_acknowledgement", "incident_log",
				"incident", "elder_family_binding", "elder", "family_member", "user_role", "app_user")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("""
				INSERT INTO app_user (id, username, password_hash, display_name) VALUES
				(7, 'family-a', '{noop}test-password', 'Family A'), (9, 'family-b', '{noop}test-password', 'Family B'),
				(10, 'manager', '{noop}test-password', 'Manager'), (12, 'no-profile', '{noop}test-password', 'No profile'),
				(13, 'caregiver', '{noop}test-password', 'Caregiver'), (14, 'elder', '{noop}test-password', 'Elder')
				""");
		jdbc.update("INSERT INTO user_role (user_id, role) VALUES (7, 'FAMILY'), (9, 'FAMILY'), (10, 'MANAGER'), (12, 'FAMILY'), (13, 'CAREGIVER'), (14, 'ELDER')");
		jdbc.update("INSERT INTO family_member (id, user_id, full_name) VALUES (42, 7, 'Family A'), (7, 9, 'Family B')");
		jdbc.update("INSERT INTO elder (id, full_name) VALUES (101, 'Elder A'), (102, 'Elder B'), (103, 'Elder C')");
		jdbc.update("""
				INSERT INTO elder_family_binding (elder_id, family_member_id, relationship, access_scope, status) VALUES
				(101, 42, 'DAUGHTER', 'FULL', 'ACTIVE'), (102, 42, 'SON', 'READ_ONLY', 'ACTIVE'), (101, 7, 'SON', 'FULL', 'ACTIVE')
				""");
		for (long id : List.of(601L, 602L, 603L)) {
			jdbc.update("""
					INSERT INTO incident (id, elder_id, responder_user_id, source, category, severity, status, description, reported_at, respond_by)
					VALUES (?, ?, 10, 'CAREGIVER', 'FALL', 'HIGH', ?, 'Care details', ?, ?)
					""", id, id - 500, id == 602 ? "RESOLVED" : "OPEN",
					Timestamp.valueOf(LocalDateTime.of(2026, 10, 7, 15, 0)), Timestamp.valueOf(LocalDateTime.of(2026, 10, 7, 15, 5)));
		}
		jdbc.update("""
				INSERT INTO notification (id, recipient_user_id, event_type, title, resource_type, resource_id, status)
				VALUES (801, 7, 'INCIDENT_RAISED', 'Care alert', 'INCIDENT', 601, 'SENT')
				""");
	}

	@Test
	void fractionalClockReturnsThePersistedFirstReceiptAcrossReloadAndRepeat() throws Exception {
		Browser family = loginAs("family-a");
		clock.now = START.plusNanos(987_654_321);
		var viewed = command(family, 601, "view", null);
		assertReceipt(viewed, 601, 42, "2026-10-07T16:00:00+08:00", null, null);
		clock.now = START.plusSeconds(60).plusNanos(987_654_321);
		var aware = command(family, 601, "acknowledge", "{\"responseNote\":\"First note\"}");
		assertReceipt(aware, 601, 42, "2026-10-07T16:00:00+08:00", "2026-10-07T16:01:00+08:00", "First note");
		var detail = json.readTree(mvc.perform(get("/api/family/incidents/601").session(family.session()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(detail.path("acknowledgement")).isEqualTo(aware);
		clock.now = START.plusSeconds(120).plusNanos(123_456_789);
		assertThat(command(family, 601, "view", null)).isEqualTo(aware);
		assertThat(command(family, 601, "acknowledge", "{\"responseNote\":\"Do not replace\"}")).isEqualTo(aware);
	}

	@Test
	void viewThenAcknowledgePreservesFirstTimesAndNoteWithoutChangingHandlingOrRead() throws Exception {
		var incidents = jdbc.queryForList("SELECT * FROM incident ORDER BY id");
		var notices = jdbc.queryForList("SELECT * FROM notification ORDER BY id");
		Browser family = loginAs("family-a");
		JsonNode viewed = command(family, 601, "view", null);
		assertReceipt(viewed, 601, 42, "2026-10-07T16:00:00+08:00", null, null);
		clock.now = START.plusSeconds(60);
		JsonNode acknowledged = command(family, 601, "acknowledge", "{\"responseNote\":\"I know\"}");
		assertReceipt(acknowledged, 601, 42, "2026-10-07T16:00:00+08:00", "2026-10-07T16:01:00+08:00", "I know");
		clock.now = START.plusSeconds(120);
		assertThat(command(family, 601, "acknowledge", "{\"responseNote\":\"Replace it\"}")).isEqualTo(acknowledged);
		assertThat(command(family, 601, "view", null)).isEqualTo(acknowledged);
		assertThat(jdbc.queryForList("SELECT * FROM incident ORDER BY id")).isEqualTo(incidents);
		assertThat(jdbc.queryForList("SELECT * FROM notification ORDER BY id")).isEqualTo(notices);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident_log", Long.class)).isZero();
		assertThat(read(family, 601).path("acknowledgement")).isEqualTo(acknowledged);
	}

	@Test
	void acknowledgeFirstKeepsNullNoteOnRetryAndViewRemainsIndependent() throws Exception {
		Browser family = loginAs("family-a");
		JsonNode acknowledged = command(family, 601, "acknowledge", null);
		assertReceipt(acknowledged, 601, 42, null, "2026-10-07T16:00:00+08:00", null);
		clock.now = START.plusSeconds(60);
		assertThat(command(family, 601, "acknowledge", "{\"responseNote\":\"Later note\"}")).isEqualTo(acknowledged);
		assertReceipt(command(family, 601, "view", null), 601, 42,
				"2026-10-07T16:01:00+08:00", "2026-10-07T16:00:00+08:00", null);
	}

	@ParameterizedTest
	@ValueSource(strings = {"view", "acknowledge"})
	void independentFamiliesReadOnlyBindingAndResolvedIncidentNeedNoNotification(String action) throws Exception {
		jdbc.update("DELETE FROM notification");
		JsonNode a = command(loginAs("family-a"), 601, action, null);
		JsonNode b = command(loginAs("family-b"), 601, action, null);
		assertThat(a.path("familyMemberId").longValue()).isEqualTo(42);
		assertThat(b.path("familyMemberId").longValue()).isEqualTo(7);
		assertThat(a.path("id").longValue()).isNotEqualTo(b.path("id").longValue());
		assertThat(command(loginAs("family-a"), 602, action, null).path("incidentId").longValue()).isEqualTo(602);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident_acknowledgement", Long.class)).isEqualTo(3);
	}

	@ParameterizedTest
	@ValueSource(strings = {"view", "acknowledge"})
	void currentBindingAndAccountChangesBlockFurtherWritesInTheSameSession(String action) throws Exception {
		Browser family = loginAs("family-a");
		command(family, 601, action, null);
		var before = jdbc.queryForList("SELECT * FROM incident_acknowledgement");
		jdbc.update("UPDATE elder_family_binding SET status = 'REVOKED' WHERE elder_id = 101 AND family_member_id = 42");
		mvc.perform(request(family, 601, action, null)).andExpect(status().isForbidden());
		jdbc.update("UPDATE elder_family_binding SET status = 'ACTIVE' WHERE elder_id = 101 AND family_member_id = 42");
		jdbc.update("UPDATE app_user SET enabled = false WHERE id = 7");
		mvc.perform(request(family, 601, action, null)).andExpect(status().isForbidden());
		jdbc.update("UPDATE app_user SET enabled = true WHERE id = 7");
		jdbc.update("DELETE FROM user_role WHERE user_id = 7");
		mvc.perform(request(family, 601, action, null)).andExpect(status().isForbidden());
		assertThat(jdbc.queryForList("SELECT * FROM incident_acknowledgement")).isEqualTo(before);
	}

	@ParameterizedTest
	@ValueSource(strings = {"view", "acknowledge"})
	void expiredBindingAndWrongActualElderDenyClientIdentityOverrides(String action) throws Exception {
		Browser family = loginAs("family-a");
		jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 102", Timestamp.valueOf(LocalDateTime.of(2026, 10, 7, 16, 0)));
		mvc.perform(request(family, 602, action, null)).andExpect(status().isForbidden());
		mvc.perform(request(family, 603, action, null).param("elderId", "101").param("familyMemberId", "7"))
				.andExpect(status().isForbidden());
		assertNoReceipt();
	}

	@ParameterizedTest
	@ValueSource(strings = {"view", "acknowledge"})
	void realSecurityChainRejectsAnonymousOtherRolesMissingProfileAndCsrf(String action) throws Exception {
		Browser family = loginAs("family-a");
		mvc.perform(post("/api/incidents/601/" + action).cookie(family.csrf()).header("X-XSRF-TOKEN", family.csrf().getValue()))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/incidents/601/" + action).session(family.session())).andExpect(status().isForbidden());
		mvc.perform(post("/api/incidents/601/" + action).session(family.session()).cookie(family.csrf())
				.header("X-XSRF-TOKEN", "wrong")).andExpect(status().isForbidden());
		for (String user : List.of("manager", "caregiver", "elder", "no-profile")) {
			mvc.perform(request(loginAs(user), 601, action, null)).andExpect(status().isForbidden());
		}
		assertNoReceipt();
	}

	@ParameterizedTest
	@ValueSource(strings = {"view", "acknowledge"})
	void unknownAndMalformedIncidentIdsHaveNoReceipt(String action) throws Exception {
		Browser family = loginAs("family-a");
		mvc.perform(request(family, 999, action, null)).andExpect(status().isNotFound());
		mvc.perform(post("/api/incidents/not-a-number/" + action).session(family.session()).cookie(family.csrf())
				.header("X-XSRF-TOKEN", family.csrf().getValue())).andExpect(status().isBadRequest());
		assertNoReceipt();
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"responseNote\":null}", "{\"responseNote\":\"\"}"})
	void optionalNoteShapesAreAccepted(String body) throws Exception {
		command(loginAs("family-a"), 601, "acknowledge", body);
	}

	@Test
	void validatesNoteLengthWithoutTrimmingOrChangingPlainText() throws Exception {
		Browser family = loginAs("family-a");
		mvc.perform(request(family, 601, "acknowledge", json.writeValueAsString(Map.of("responseNote", "知".repeat(256)))))
				.andExpect(status().isBadRequest());
		assertNoReceipt();
		String prefix = " <script>plain text</script> ";
		String note = prefix + "知".repeat(255 - prefix.length());
		JsonNode result = command(family, 601, "acknowledge", json.writeValueAsString(Map.of("responseNote", note)));
		assertThat(result.path("responseNote").asString()).isEqualTo(note);
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"familyMemberId\":7}", "{\"acknowledgedAt\":\"2000-01-01T00:00:00Z\"}",
			"{\"responseNote\":\"Hi\",\"familyMemberId\":7}",
			"{\"responseNote\":123}", "{\"responseNote\":[]}", "[]", "{broken"})
	void rejectsUnexpectedFieldsAndInvalidJsonShapes(String body) throws Exception {
		mvc.perform(request(loginAs("family-a"), 601, "acknowledge", body)).andExpect(status().isBadRequest());
		assertNoReceipt();
	}

	@Test
	void viewAcceptsNoBusinessBody() throws Exception {
		mvc.perform(request(loginAs("family-a"), 601, "view", "{\"viewedAt\":\"2000-01-01T00:00:00Z\"}"))
				.andExpect(status().isBadRequest());
		assertNoReceipt();
	}

	@ParameterizedTest
	@ValueSource(strings = {"view", "acknowledge"})
	void concurrentFirstWritesAndRetriesReturnOneStableReceipt(String action) throws Exception {
		Browser family = loginAs("family-a");
		var start = new CountDownLatch(1);
		try (var pool = Executors.newFixedThreadPool(6)) {
			var futures = new ArrayList<java.util.concurrent.Future<JsonNode>>();
			for (int index = 0; index < 6; index++) {
				String body = action.equals("acknowledge") ? "{\"responseNote\":\"Note " + index + "\"}" : null;
				futures.add(pool.submit(() -> { assertThat(start.await(10, TimeUnit.SECONDS)).isTrue(); return command(family, 601, action, body); }));
			}
			start.countDown();
			JsonNode first = futures.getFirst().get(20, TimeUnit.SECONDS);
			for (var future : futures) { assertThat(future.get(20, TimeUnit.SECONDS)).isEqualTo(first); }
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident_acknowledgement", Long.class)).isEqualTo(1);
			clock.now = START.plusSeconds(60);
			assertThat(command(family, 601, action, action.equals("acknowledge") ? "{\"responseNote\":\"retry\"}" : null)).isEqualTo(first);
		}
	}

	@Test
	void concurrentViewAndAcknowledgementKeepBothIndependentFields() throws Exception {
		Browser family = loginAs("family-a");
		var start = new CountDownLatch(1);
		try (var pool = Executors.newFixedThreadPool(2)) {
			var view = pool.submit(() -> { start.await(); return command(family, 601, "view", null); });
			var ack = pool.submit(() -> { start.await(); return command(family, 601, "acknowledge", "{\"responseNote\":\"Known\"}"); });
			start.countDown();
			view.get(20, TimeUnit.SECONDS);
			ack.get(20, TimeUnit.SECONDS);
		}
		assertReceipt(read(family, 601).path("acknowledgement"), 601, 42,
				"2026-10-07T16:00:00+08:00", "2026-10-07T16:00:00+08:00", "Known");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident_acknowledgement", Long.class)).isEqualTo(1);
	}

	@Test
	void auditRecordsSuccessDenialAndFailureWithoutCareContentOrNote() throws Exception {
		Browser family = loginAs("family-a");
		command(family, 601, "view", null);
		command(family, 601, "acknowledge", "{\"responseNote\":\"Private reply\"}");
		mvc.perform(request(family, 603, "acknowledge", null)).andExpect(status().isForbidden());
		mvc.perform(request(family, 999, "view", null)).andExpect(status().isNotFound());
		var rows = jdbc.queryForList("SELECT * FROM audit_log ORDER BY id");
		assertThat(rows).extracting(row -> row.get("result")).containsExactly("OK", "OK", "DENIED", "FAILED");
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.get("actor_user_id")).isEqualTo(7L);
			assertThat(row.get("action")).isEqualTo("UPDATE");
			assertThat(row.get("resource_type")).isEqualTo("INCIDENT");
			assertThat(row.get("detail")).isIn("FM05_VIEW_INCIDENT", "FM05_ACKNOWLEDGE_INCIDENT");
		});
		assertThat(auditRows.findAll()).extracting(row -> row.getOccurredAt())
				.containsOnly(LocalDateTime.of(2026, 10, 7, 16, 0));
	}

	@ParameterizedTest
	@ValueSource(strings = {"view", "acknowledge"})
	void auditOutageRollsBackReceiptAndRetryCanSucceed(String action) throws Exception {
		Browser family = loginAs("family-a");
		jdbc.execute("RENAME TABLE audit_log TO fm05_missing_audit");
		try {
			mvc.perform(request(family, 601, action, null)).andExpect(status().isServiceUnavailable());
			assertNoReceipt();
		} finally { jdbc.execute("RENAME TABLE fm05_missing_audit TO audit_log"); }
		command(family, 601, action, null);
		var existing = jdbc.queryForList("SELECT * FROM incident_acknowledgement");
		jdbc.execute("RENAME TABLE audit_log TO fm05_missing_audit");
		try {
			mvc.perform(request(family, 601, action.equals("view") ? "acknowledge" : "view", null))
					.andExpect(status().isServiceUnavailable());
			assertThat(jdbc.queryForList("SELECT * FROM incident_acknowledgement")).isEqualTo(existing);
		} finally { jdbc.execute("RENAME TABLE fm05_missing_audit TO audit_log"); }
	}

	@Test
	void receiptSaveFailureRollsBackUpdateAndDoesNotAuditSuccess() throws Exception {
		Browser family = loginAs("family-a");
		command(family, 601, "view", null);
		var before = jdbc.queryForList("SELECT * FROM incident_acknowledgement");
		jdbc.execute("""
				CREATE TRIGGER fm05_block_receipt BEFORE UPDATE ON incident_acknowledgement FOR EACH ROW
				BEGIN
				  IF NEW.acknowledged_at IS NOT NULL THEN
				    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Forced persistence failure';
				  END IF;
				END
				""");
		try {
			mvc.perform(request(family, 601, "acknowledge", "{\"responseNote\":\"No false success\"}"))
					.andExpect(status().isInternalServerError());
			assertThat(jdbc.queryForList("SELECT * FROM incident_acknowledgement")).isEqualTo(before);
			assertThat(jdbc.queryForList("SELECT result FROM audit_log ORDER BY id")).extracting(row -> row.get("result"))
					.containsExactly("OK", "FAILED");
		} finally { jdbc.execute("DROP TRIGGER fm05_block_receipt"); }
	}

	private record Browser(MockHttpSession session, Cookie csrf) { }

	private Browser loginAs(String username) throws Exception {
		var token = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		var result = mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username", username, "password", "test-password"))))
				.andExpect(status().isOk()).andReturn();
		return new Browser((MockHttpSession) result.getRequest().getSession(false), token);
	}

	private MockHttpServletRequestBuilder request(Browser browser, long id, String action, String body) {
		var request = post("/api/incidents/{id}/" + action, id).session(browser.session()).cookie(browser.csrf())
				.header("X-XSRF-TOKEN", browser.csrf().getValue());
		return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private JsonNode command(Browser browser, long id, String action, String body) throws Exception {
		return json.readTree(mvc.perform(request(browser, id, action, body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private JsonNode read(Browser browser, long id) throws Exception {
		return json.readTree(mvc.perform(get("/api/family/incidents/{id}", id).session(browser.session()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private void assertNoReceipt() { assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident_acknowledgement", Long.class)).isZero(); }

	private static void assertReceipt(JsonNode body, long incidentId, long familyId, String view, String ack, String note) {
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("id", "incidentId", "familyMemberId", "viewedAt", "acknowledgedAt", "responseNote");
		assertThat(body.path("id").longValue()).isPositive();
		assertThat(body.path("incidentId").longValue()).isEqualTo(incidentId);
		assertThat(body.path("familyMemberId").longValue()).isEqualTo(familyId);
		assertThat(body.path("viewedAt").isNull() ? null : body.path("viewedAt").asString()).isEqualTo(view);
		assertThat(body.path("acknowledgedAt").isNull() ? null : body.path("acknowledgedAt").asString()).isEqualTo(ack);
		assertThat(body.path("responseNote").isNull() ? null : body.path("responseNote").asString()).isEqualTo(note);
	}
}
