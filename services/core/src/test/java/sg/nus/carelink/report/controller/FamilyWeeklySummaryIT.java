package sg.nus.carelink.report.controller;

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
 * Family weekly summaries through real login sessions, current bindings, MySQL and auditing.
 *
 * @author Wang Zhili
 */
@SpringBootTest(properties = {"carelink.report.schedule-cron=-", "carelink.escalation.scan-initial-delay=PT1H"})
@AutoConfigureMockMvc
@Import(FamilyWeeklySummaryIT.FixedTime.class)
class FamilyWeeklySummaryIT {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 0, 30);
	private static final String DISCLAIMER = "This summary records care observations and services; it is not a diagnosis or medical advice.";
	private static final String CONTENT = """
			{"sections":[{"title":"Service completion","body":"Three visits completed."},{"title":"Observations","body":"Walked two laps."}],"dataComplete":true,"missingItems":[],"disclaimer":null,"generatedBy":"TEMPLATE"}
			""";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyWeeklySummaryIT.class, "+05:00");
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
		Clock familySummaryClock() {
			return Clock.fixed(Instant.parse("2026-09-27T16:30:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void prepareIsolatedReports() {
		jdbc.update("DELETE FROM audit_log");
		jdbc.update("DELETE FROM report_amendment");
		jdbc.update("DELETE FROM report");
		jdbc.update("DELETE FROM vital_sign");
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
		jdbc.update("""
				INSERT INTO elder_family_binding (elder_id, family_member_id, relationship, access_scope, status) VALUES
				(101, 42, 'DAUGHTER', 'FULL', 'ACTIVE'),
				(102, 42, 'DAUGHTER', 'READ_ONLY', 'ACTIVE'),
				(110, 7, 'DAUGHTER', 'FULL', 'ACTIVE')
				""");
		seedReport(301, 101, "FAMILY", "PUBLISHED", CONTENT);
		seedReport(302, 102, "FAMILY", "ARCHIVED", CONTENT);
		seedReport(310, 110, "FAMILY", "PUBLISHED", CONTENT);
		seedReport(320, 101, "FAMILY", "DRAFT", null);
		seedReport(321, 101, "INTERNAL", "PUBLISHED", CONTENT);
		seedReport(322, 101, "REGULATOR", "PUBLISHED", CONTENT);
	}

	@Test
	void readsStoredChaptersAsTemplateWithTheSelectedReportIdentity() throws Exception {
		var body = readSummary(loginAs("family-a"), 101, "2026-09-21");
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("reportId", "elderId", "periodStart", "periodEnd",
				"summaryText", "generatedBy", "disclaimer");
		assertThat(body.path("reportId").longValue()).isEqualTo(301);
		assertThat(body.path("elderId").longValue()).isEqualTo(101);
		assertThat(body.path("periodStart").asString()).isEqualTo("2026-09-21");
		assertThat(body.path("periodEnd").asString()).isEqualTo("2026-09-27");
		assertThat(body.path("summaryText").asString())
				.isEqualTo("Service completion\nThree visits completed.\n\nObservations\nWalked two laps.");
		assertThat(body.path("generatedBy").asString()).isEqualTo("TEMPLATE");
		assertThat(body.path("disclaimer").asString()).isEqualTo(DISCLAIMER);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "abc", "2026-09-22", "2026-09-27", "2026-02-29", "2026-13-01",
			"2026-9-21", "2026-09-21T00:00:00", "0001-01-01", "0999-12-30", "9999-12-27",
			"+10000-01-03", "+999999999-12-27"})
	void invalidOrUnsupportedWeeksReturn400WithoutSummary(String weekStart) throws Exception {
		mvc.perform(get("/api/elders/101/weekly-summary").session(loginAs("family-a")).param("weekStart", weekStart))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.summaryText").doesNotExist());
	}

	@Test
	void picksLatestReadableExactPeriodWithoutChangingManagerGenerationIdempotency() throws Exception {
		seedReport(401, 101, "FAMILY", "PUBLISHED", CONTENT);
		seedReport(402, 101, "FAMILY", "ARCHIVED", CONTENT);
		seedReport(900, 101, "FAMILY", "PUBLISHED", CONTENT);
		jdbc.update("UPDATE report SET created_at = ? WHERE id IN (401, 402)", NOW.plusHours(1));
		jdbc.update("UPDATE report SET created_at = ? WHERE id IN (310, 320, 321, 322)", NOW.plusHours(2));
		jdbc.update("UPDATE report SET content = NULL WHERE id IN (310, 320, 321, 322)");
		assertThat(readSummary(loginAs("family-a"), 101, "2026-09-21").path("reportId").longValue()).isEqualTo(402);
		// Restore the manager's other audiences: its lookup must still return the earliest IDs.
		jdbc.update("UPDATE report SET content = ? WHERE id IN (321, 322)", CONTENT);
		var before = jdbc.queryForList("SELECT * FROM report ORDER BY id");
		var generated = managerPost(loginAs("manager"), "/api/reports/generate",
				Map.of("elderId", 101, "periodStart", "2026-09-21", "periodEnd", "2026-09-27"), 202);
		assertThat(generated).hasSize(3);
		assertThat(generated).filteredOn(node -> node.path("audience").asString().equals("FAMILY"))
				.extracting(node -> node.path("id").longValue()).containsExactly(301L);
		assertThat(jdbc.queryForList("SELECT * FROM report ORDER BY id")).isEqualTo(before);
	}

	@Test
	void missingExactWeekDoesNotSelectOverlappingOrForbiddenReportsOrCreateContent() throws Exception {
		jdbc.update("UPDATE report SET period_end = '2026-09-26' WHERE id = 301");
		seedReport(401, 101, "FAMILY", "ARCHIVED", null);
		seedReport(402, 101, "FAMILY", "PUBLISHED", null);
		jdbc.update("UPDATE report SET period_start = '2026-09-20' WHERE id = 401");
		jdbc.update("UPDATE report SET period_start = '2026-09-28', period_end = '2026-10-04' WHERE id = 402");
		jdbc.update("UPDATE report SET content = NULL WHERE id IN (320, 321, 322)");
		var before = jdbc.queryForList("SELECT * FROM report ORDER BY id");
		var session = loginAs("family-a");
		for (String week : List.of("2026-09-21", "2026-09-14")) {
			mvc.perform(get("/api/elders/101/weekly-summary").session(session).param("weekStart", week))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.summaryText").doesNotExist());
		}
		assertThat(jdbc.queryForList("SELECT * FROM report ORDER BY id")).isEqualTo(before);
	}

	@ParameterizedTest
	@CsvSource({"2026-08-31,2026-09-06", "2025-12-29,2026-01-04", "2024-02-26,2024-03-03",
			"1000-01-06,1000-01-12", "9999-12-20,9999-12-26"})
	void readsExactCalendarWeeksAcrossMonthYearLeapDayAndSupportedDateBounds(String start, String end) throws Exception {
		jdbc.update("UPDATE report SET period_start = ?, period_end = ? WHERE id = 301", start, end);
		var body = readSummary(loginAs("family-a"), 101, start);
		assertThat(body.path("reportId").longValue()).isEqualTo(301);
		assertThat(body.path("periodStart").asString()).isEqualTo(start);
		assertThat(body.path("periodEnd").asString()).isEqualTo(end);
	}

	@Test
	void weekIsRequiredAndReadOnlyBindingsCanReadArchivedSummaries() throws Exception {
		var session = loginAs("family-a");
		mvc.perform(get("/api/elders/101/weekly-summary").session(session)).andExpect(status().isBadRequest());
		assertThat(readSummary(session, 102, "2026-09-21").path("reportId").longValue()).isEqualTo(302);
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "9223372036854775808"})
	void malformedElderIdsReturn400(String id) throws Exception {
		mvc.perform(get("/api/elders/{id}/weekly-summary", id).session(loginAs("family-a"))
				.param("weekStart", "2026-09-21")).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(longs = {110, 999, 0, -1, Long.MAX_VALUE})
	void inaccessibleAndUnknownEldersAreForbiddenBeforeContentIsParsed(long elderId) throws Exception {
		jdbc.update("UPDATE report SET content = NULL WHERE elder_id = 110");
		mvc.perform(get("/api/elders/{id}/weekly-summary", elderId).session(loginAs("family-a"))
				.param("weekStart", "2026-09-21")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.summaryText").doesNotExist());
	}

	@Test
	void callerSuppliedIdentityCannotOverrideTheSession() throws Exception {
		var session = loginAs("family-b");
		mvc.perform(get("/api/elders/101/weekly-summary").session(session).param("weekStart", "2026-09-21")
				.param("familyMemberId", "42").param("userId", "7").param("role", "MANAGER").param("elderId", "110"))
				.andExpect(status().isForbidden());
		assertThat(readSummary(session, 110, "2026-09-21").path("reportId").longValue()).isEqualTo(310);
	}

	@ParameterizedTest
	@ValueSource(strings = {"manager", "caregiver", "elder", "no-profile", "no-binding"})
	void unsupportedAccountsCannotReadSummaries(String username) throws Exception {
		mvc.perform(get("/api/elders/101/weekly-summary").session(loginAs(username)).param("weekStart", "2026-09-21"))
				.andExpect(status().isForbidden());
	}

	@Test
	void anonymousAndDisabledAccountsCannotReadSummaries() throws Exception {
		mvc.perform(get("/api/elders/101/weekly-summary").param("weekStart", "2026-09-21"))
				.andExpect(status().isUnauthorized());
		var session = loginAs("family-a");
		jdbc.update("UPDATE app_user SET enabled = false WHERE id = 7");
		mvc.perform(get("/api/elders/101/weekly-summary").session(session).param("weekStart", "2026-09-21"))
				.andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = {"REVOKED", "REJECTED", "PENDING_CONFIRMATION"})
	void bindingStatusIsCheckedAgainInTheSameSession(String bindingStatus) throws Exception {
		var session = loginAs("family-a");
		readSummary(session, 101, "2026-09-21");
		jdbc.update("UPDATE elder_family_binding SET status = ? WHERE elder_id = 101", bindingStatus);
		mvc.perform(get("/api/elders/101/weekly-summary").session(session).param("weekStart", "2026-09-21"))
				.andExpect(status().isForbidden());
	}

	@Test
	void expiryUsesSingaporeTimeAndTakesEffectAtTheExactInstant() throws Exception {
		var session = loginAs("family-a");
		jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 101", NOW.plusSeconds(1));
		readSummary(session, 101, "2026-09-21");
		for (LocalDateTime expiresAt : List.of(NOW, NOW.minusSeconds(1))) {
			jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 101", expiresAt);
			mvc.perform(get("/api/elders/101/weekly-summary").session(session).param("weekStart", "2026-09-21"))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void templatePreservesVisibleChapterOrderAndTextWhileDetailKeepsCompletenessAndCorrections() throws Exception {
		jdbc.update("UPDATE report SET content = ? WHERE id = 301", json.writeValueAsString(Map.of(
				"sections", List.of(
						Map.of("title", "Observations", "body", "Mei: walked two laps.\n\nRecorded text: <b>steady</b>; BP < 140."),
						Map.of("title", "Staff performance", "body", "Internal assessment"),
						Map.of("title", "Vital signs", "body", "Systolic 128–142 mmHg"),
						Map.of("title", "Incidents", "body", "")),
				"dataComplete", false, "missingItems", List.of("Visit 201 on 2026-09-22 not closed"),
				"disclaimer", "Stored disclaimer", "generatedBy", "MODEL")));
		jdbc.update("""
				INSERT INTO report_amendment (id, report_id, note, author_user_id, created_at)
				VALUES (501, 301, 'Correction: three laps.', 10, '2026-09-28 01:00:00')
				""");
		var before = jdbc.queryForList("SELECT * FROM report ORDER BY id");
		var amendmentsBefore = jdbc.queryForList("SELECT * FROM report_amendment ORDER BY id");
		var session = loginAs("family-a");
		var summary = readSummary(session, 101, "2026-09-21");
		assertThat(summary.path("summaryText").asString()).isEqualTo(
				"Observations\nMei: walked two laps.\n\nRecorded text: <b>steady</b>; BP < 140.\n\nVital signs\nSystolic 128–142 mmHg\n\nIncidents\n");
		assertThat(summary.path("generatedBy").asString()).isEqualTo("TEMPLATE");
		assertThat(summary.path("disclaimer").asString()).isEqualTo(DISCLAIMER);
		var detail = readDetail(session, summary.path("reportId").longValue());
		assertThat(detail.path("sections")).extracting(node -> node.path("title").asString())
				.containsExactly("Observations", "Vital signs", "Incidents");
		assertThat(detail.path("generatedBy").asString()).isEqualTo("MODEL");
		assertThat(detail.path("dataComplete").booleanValue()).isFalse();
		assertThat(detail.path("missingItems")).extracting(JsonNode::asString).containsExactly("Visit 201 on 2026-09-22 not closed");
		assertThat(detail.path("amendments")).hasSize(1);
		assertThat(detail.path("amendments").get(0).path("note").asString()).isEqualTo("Correction: three laps.");
		assertThat(jdbc.queryForList("SELECT * FROM report ORDER BY id")).isEqualTo(before);
		assertThat(jdbc.queryForList("SELECT * FROM report_amendment ORDER BY id")).isEqualTo(amendmentsBefore);
	}

	@Test
	void reportWithoutAnyFamilyChaptersFailsInsteadOfInventingASuccessfulSummary() throws Exception {
		jdbc.update("UPDATE report SET content = ? WHERE id = 301", json.writeValueAsString(Map.of(
				"sections", List.of(Map.of("title", "Staff performance", "body", "Internal assessment")),
				"dataComplete", true, "missingItems", List.of(), "generatedBy", "TEMPLATE")));
		mvc.perform(get("/api/elders/101/weekly-summary").session(loginAs("family-a")).param("weekStart", "2026-09-21"))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.summaryText").doesNotExist());
		assertThat(jdbc.queryForList("SELECT result FROM audit_log WHERE detail LIKE 'FM04_READ_WEEKLY_SUMMARY;%'"))
				.extracting(entry -> entry.get("result")).containsExactly("FAILED");
	}

	@Test
	void auditRecordsOneOutcomePerReadWithElderAndWeekAndNoContent() throws Exception {
		var session = loginAs("family-a");
		readSummary(session, 101, "2026-09-21");
		mvc.perform(get("/api/elders/110/weekly-summary").session(session).param("weekStart", "2026-09-21"))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/elders/101/weekly-summary").session(session).param("weekStart", "2026-09-14"))
				.andExpect(status().isNotFound());
		jdbc.update("UPDATE report SET content = NULL WHERE id = 301");
		mvc.perform(get("/api/elders/101/weekly-summary").session(session).param("weekStart", "2026-09-21"))
				.andExpect(status().isInternalServerError());
		var entries = jdbc.queryForList("SELECT * FROM audit_log WHERE action = 'READ' ORDER BY id");
		assertThat(entries).extracting(entry -> entry.get("result")).containsExactly("OK", "DENIED", "FAILED", "FAILED");
		assertThat(entries).extracting(entry -> entry.get("resource_id")).containsExactly(101L, 110L, 101L, 101L);
		assertThat(entries).extracting(entry -> entry.get("detail")).containsExactly(
				"FM04_READ_WEEKLY_SUMMARY;weekStart=2026-09-21", "FM04_READ_WEEKLY_SUMMARY;weekStart=2026-09-21",
				"FM04_READ_WEEKLY_SUMMARY;weekStart=2026-09-14", "FM04_READ_WEEKLY_SUMMARY;weekStart=2026-09-21");
		assertThat(entries).allSatisfy(entry -> {
			assertThat(entry.get("actor_user_id")).isEqualTo(7L);
			assertThat(entry.get("resource_type")).isEqualTo("ELDER");
			assertThat(entry.get("occurred_at")).isNotNull();
		});
	}

	@Test
	void auditOutageReturns503WithoutSummaryForSuccessfulDeniedAndMissingReads() throws Exception {
		var session = loginAs("family-a");
		jdbc.execute("RENAME TABLE audit_log TO fm04_summary_audit_unavailable");
		try {
			for (long elderId : List.of(101L, 110L)) {
				mvc.perform(get("/api/elders/{id}/weekly-summary", elderId).session(session).param("weekStart", "2026-09-21"))
						.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.summaryText").doesNotExist());
			}
			mvc.perform(get("/api/elders/101/weekly-summary").session(session).param("weekStart", "2026-09-14"))
					.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.summaryText").doesNotExist());
		} finally {
			jdbc.execute("RENAME TABLE fm04_summary_audit_unavailable TO audit_log");
		}
	}

	@Test
	void readsARealManagerGeneratedQuietWeekWithoutRegeneratingOrChangingIt() throws Exception {
		var generated = managerPost(loginAs("manager"), "/api/reports/generate",
				Map.of("elderId", 101, "periodStart", "2026-09-14", "periodEnd", "2026-09-20"), 202);
		assertThat(generated).hasSize(3);
		long familyId = 0;
		for (JsonNode report : generated) {
			if (report.path("audience").asString().equals("FAMILY")) {
				familyId = report.path("id").longValue();
			}
		}
		assertThat(familyId).isPositive();
		var before = jdbc.queryForList("SELECT * FROM report ORDER BY id");
		var session = loginAs("family-a");
		var body = readSummary(session, 101, "2026-09-14");
		assertThat(body.path("reportId").longValue()).isEqualTo(familyId);
		assertThat(body.path("summaryText").asString()).isEqualTo(
				"Service completion\nNo visits were scheduled in this period.\n\n"
				+ "Vital signs\nNo vital signs were recorded in this period.\n\n"
				+ "Observations\nNo observations were recorded in this period.\n\n"
				+ "Incidents\nNo incidents were reported in this period.");
		assertThat(body.path("generatedBy").asString()).isEqualTo("TEMPLATE");
		assertThat(readDetail(session, familyId).path("sections")).hasSize(4);
		assertThat(readSummary(session, 101, "2026-09-14")).isEqualTo(body);
		assertThat(jdbc.queryForList("SELECT * FROM report ORDER BY id")).isEqualTo(before);
	}

	private JsonNode readSummary(MockHttpSession session, long elderId, String weekStart) throws Exception {
		var response = mvc.perform(get("/api/elders/{elderId}/weekly-summary", elderId).session(session)
				.param("weekStart", weekStart)).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn().getResponse();
		return json.readTree(response.getContentAsString());
	}

	private JsonNode managerPost(MockHttpSession session, String path, Map<String, ?> body, int expectedStatus) throws Exception {
		Cookie token = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		var response = mvc.perform(post(path).session(session).cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
				.andExpect(status().is(expectedStatus)).andReturn().getResponse();
		return json.readTree(response.getContentAsString());
	}

	private JsonNode readDetail(MockHttpSession session, long id) throws Exception {
		var response = mvc.perform(get("/api/reports/{id}", id).session(session)).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn().getResponse();
		return json.readTree(response.getContentAsString());
	}

	private void seedReport(long id, long elderId, String audience, String reportStatus, String reportContent) {
		jdbc.update("""
				INSERT INTO report (id, elder_id, generated_by_user_id, audience, period_start, period_end, status, content, created_at)
				VALUES (?, ?, 10, ?, '2026-09-21', '2026-09-27', ?, ?, ?)
				""", id, elderId, audience, reportStatus, reportContent, NOW);
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
