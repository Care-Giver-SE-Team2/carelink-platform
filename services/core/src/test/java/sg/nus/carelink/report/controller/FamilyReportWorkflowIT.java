package sg.nus.carelink.report.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.testsupport.SharedMySql;

/**
 * MG07 generation to FM04 reading over real HTTP sessions and isolated MySQL.
 * Care facts and active bindings are fixtures; report content is generated through the API.
 *
 * @author Wang Zhili
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {"carelink.report.schedule-cron=-", "carelink.escalation.scan-initial-delay=PT1H"})
@Import(FamilyReportWorkflowIT.FixedTime.class)
class FamilyReportWorkflowIT {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 0, 30);
	private static final String DISCLAIMER = "This summary records care observations and services; it is not a diagnosis or medical advice.";
	private static final String OBSERVATION = "Walked two laps.\n\n  Follow-up: rest after activity.";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyReportWorkflowIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
	}

	@LocalServerPort
	private int port;
	@Autowired
	private JdbcTemplate jdbc;
	private final JsonMapper json = JsonMapper.builder().build();

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTime {
		@Bean
		@Primary
		Clock familyReportWorkflowClock() {
			return Clock.fixed(Instant.parse("2026-09-27T16:30:00Z"), ZoneId.of("Asia/Singapore"));
		}
	}

	@BeforeEach
	void prepareIsolatedCareFacts() {
		for (String table : List.of("audit_log", "intake_application", "report_amendment", "report", "incident_log",
				"incident", "vital_sign", "visit_task", "visit_evidence", "visit", "elder_family_binding", "elder",
				"family_member", "caregiver", "user_role", "app_user")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("""
				INSERT INTO app_user (id, username, password_hash, display_name) VALUES
				(7, 'family-a', '{noop}test-password', 'Family A'),
				(9, 'family-b', '{noop}test-password', 'Family B'),
				(10, 'manager', '{noop}test-password', 'Manager'),
				(12, 'caregiver', '{noop}test-password', 'Caregiver Mei')
				""");
		jdbc.update("INSERT INTO user_role (user_id, role) VALUES (7, 'FAMILY'), (9, 'FAMILY'), (10, 'MANAGER'), (12, 'CAREGIVER')");
		jdbc.update("INSERT INTO family_member (id, user_id, full_name) VALUES (42, 7, 'Family A'), (7, 9, 'Family B')");
		jdbc.update("INSERT INTO elder (id, full_name) VALUES (101, 'Elder Tan'), (102, 'Elder Li'), (110, 'Elder Lim')");
		jdbc.update("INSERT INTO caregiver (id, user_id, full_name, status) VALUES (501, 12, 'Caregiver Mei', 'AVAILABLE')");
		jdbc.update("""
				INSERT INTO elder_family_binding (id, elder_id, family_member_id, relationship, access_scope, status) VALUES
				(701, 101, 42, 'DAUGHTER', 'FULL', 'ACTIVE'),
				(702, 102, 42, 'DAUGHTER', 'READ_ONLY', 'ACTIVE'),
				(710, 110, 7, 'SON', 'FULL', 'ACTIVE')
				""");
		visit(201, 101, "2026-09-21T09:00:00", "VERIFIED");
		visit(202, 101, "2026-09-23T09:00:00", "VERIFIED");
		visit(203, 101, "2026-09-25T09:00:00", "SCHEDULED");
		visit(204, 102, "2026-09-22T09:00:00", "VERIFIED");
		visit(210, 110, "2026-09-21T09:00:00", "VERIFIED");
		jdbc.update("INSERT INTO visit_task (visit_id, name, status, caregiver_note) VALUES (201, 'Mobility', 'DONE', ?)", OBSERVATION);
		jdbc.update("INSERT INTO visit_task (visit_id, name, status, caregiver_note) VALUES (210, 'Mobility', 'DONE', 'OTHER-FAMILY-OBSERVATION')");
		jdbc.update("INSERT INTO vital_sign (visit_id, metric, value, unit, out_of_range, recorded_at) VALUES (201, 'systolic', 128, 'mmHg', false, ?)", at("2026-09-21T09:10:00"));
		jdbc.update("INSERT INTO vital_sign (visit_id, metric, value, unit, out_of_range, recorded_at) VALUES (202, 'systolic', 142, 'mmHg', true, ?)", at("2026-09-23T09:10:00"));
		jdbc.update("""
				INSERT INTO incident (id, elder_id, reported_by_user_id, responder_user_id, source, category,
				severity, status, description, reported_at, resolved_at)
				VALUES (301, 101, 12, 10, 'CAREGIVER', 'FALL', 'MEDIUM', 'RESOLVED',
				'Slipped in the bathroom, no injury.', ?, ?)
				""", at("2026-09-22T10:00:00"), at("2026-09-22T10:30:00"));
		jdbc.update("""
				INSERT INTO incident_log (incident_id, actor, action, detail, occurred_at)
				VALUES (301, 'Manager Private (manager)', 'RESOLVED', 'INTERNAL-ONLY review', ?)
				""", at("2026-09-22T10:30:00"));
	}

	@Test
	void generatedAudienceReportsBecomeFamilyPagesDetailsAndAnExactWeeklySummary() throws Exception {
		try (var manager = browser(); var family = browser()) {
			login(manager, "manager");
			var generated = generate(manager, 101, "2026-09-21", "2026-09-27");
			assertThat(generated).extracting(node -> node.path("audience").asString())
					.containsExactly("FAMILY", "REGULATOR", "INTERNAL");
			long reportId = reportId(generated, "FAMILY");
			var managerDetail = getJson(manager, "/api/reports/" + reportId(generated, "INTERNAL"));
			assertThat(managerDetail.toString()).contains("Manager Private", "INTERNAL-ONLY review", "out of range");
			long olderId = reportId(generate(manager, 101, "2026-09-14", "2026-09-20"), "FAMILY");
			jdbc.update("UPDATE report SET status = 'ARCHIVED' WHERE id = ?", olderId);
			var originalReports = jdbc.queryForList("SELECT * FROM report ORDER BY id");

			login(family, "family-a");
			assertThat(getJson(family, "/api/auth/me").path("id").longValue()).isEqualTo(7);
			assertThat(getJson(family, "/api/elders")).extracting(node -> node.path("id").longValue())
					.containsExactlyInAnyOrder(101L, 102L);
			var first = getJson(family, "/api/reports?elderId=101&audience=FAMILY&page=0&size=1");
			assertThat(first.path("totalElements").longValue()).isEqualTo(2);
			assertThat(first.path("items")).hasSize(1);
			assertThat(first.path("items").get(0).path("id").longValue()).isEqualTo(reportId);
			assertThat(first.path("items").get(0).path("audience").asString()).isEqualTo("FAMILY");
			var second = getJson(family, "/api/reports?elderId=101&audience=FAMILY&page=1&size=1");
			assertThat(second.path("items")).hasSize(1);
			assertThat(second.path("items").get(0).path("id").longValue()).isEqualTo(olderId);
			assertThat(second.path("items").get(0).path("status").asString()).isEqualTo("ARCHIVED");
			var detail = getJson(family, "/api/reports/" + reportId);
			assertThat(detail.path("dataComplete").booleanValue()).isFalse();
			assertThat(detail.path("missingItems")).extracting(JsonNode::asString).containsExactly("Visit 203 on 2026-09-25 not closed");
			assertThat(section(detail, "Service completion")).contains("3 visits: 1 scheduled, 2 verified.", "Caregiver Mei");
			assertThat(section(detail, "Vital signs")).isEqualTo("Systolic 128–142 mmHg");
			assertThat(section(detail, "Observations")).isEqualTo("Mon 21 Sep · Caregiver Mei: " + OBSERVATION);
			assertThat(section(detail, "Incidents"))
					.isEqualTo("Tue 22 Sep 10:00 · Fall reported · Slipped in the bathroom, no injury. · resolved Tue 22 Sep 10:30");
			assertThat(detail.path("createdAt").asString()).isEqualTo("2026-09-28T00:30:00+08:00");
			assertThat(detail.path("disclaimer").asString()).isEqualTo(DISCLAIMER);
			assertThat(detail.toString()).doesNotContain("Manager Private", "INTERNAL-ONLY", "out of range", "OTHER-FAMILY");
			var summary = getJson(family, "/api/elders/101/weekly-summary?weekStart=2026-09-21");
			assertThat(summary.path("reportId").longValue()).isEqualTo(reportId);
			assertThat(summary.path("periodStart").asString()).isEqualTo("2026-09-21");
			assertThat(summary.path("periodEnd").asString()).isEqualTo("2026-09-27");
			assertThat(summary.path("generatedBy").asString()).isEqualTo("TEMPLATE");
			assertThat(summary.path("summaryText").asString()).contains("Vital signs\nSystolic 128–142 mmHg\n\nObservations\nMon 21 Sep · Caregiver Mei: " + OBSERVATION);
			assertThat(summary.path("disclaimer").asString()).isEqualTo(DISCLAIMER);
			assertThat(jdbc.queryForList("SELECT * FROM report ORDER BY id")).isEqualTo(originalReports);
			assertThat(jdbc.queryForList("SELECT * FROM report_amendment")).isEmpty();
			var audits = audits();
			assertThat(audits).hasSize(5).extracting(Audit::actor).containsOnly(7L);
			assertThat(audits).extracting(Audit::result).containsOnly("OK");
			assertThat(audits).extracting(Audit::resource).containsExactly("ELDER", "REPORT", "REPORT", "REPORT", "ELDER");
			assertThat(audits.get(1).detail()).contains("FM04_LIST_REPORTS;", "page=0", "size=1");
			assertThat(audits.get(4).detail()).contains("FM04_READ_WEEKLY_SUMMARY;", "weekStart=2026-09-21");
			assertThat(audits.toString()).doesNotContain(OBSERVATION, "128", "Slipped", "test-password");
		}
	}

	@Test
	void managerCorrectionsAppearOnRefreshWithoutRewritingTheSavedReportOrSummary() throws Exception {
		try (var manager = browser(); var family = browser()) {
			login(manager, "manager");
			var generated = generate(manager, 101, "2026-09-21", "2026-09-27");
			long id = reportId(generated, "FAMILY");
			login(family, "family-a");
			var before = getJson(family, "/api/reports/" + id);
			var summary = getJson(family, "/api/elders/101/weekly-summary?weekStart=2026-09-21");
			var originalReports = jdbc.queryForList("SELECT * FROM report ORDER BY id");
			var notes = List.of("Correction: visit 203 was completed.\n\nEvidence checked.",
					"<strong>Second correction</strong>: the original observations remain on record.");
			for (String note : notes) {
				var amendment = postJson(manager, "/api/reports/" + id + "/amendments", Map.of("note", note), 201);
				assertThat(amendment.path("authorUserId").longValue()).isEqualTo(10);
				assertThat(amendment.path("note").asString()).isEqualTo(note);
			}
			var originalAmendments = jdbc.queryForList("SELECT * FROM report_amendment ORDER BY id");
			var after = getJson(family, "/api/reports/" + id);
			assertThat(before.path("amendments")).isEmpty();
			assertThat(after.path("amendments")).extracting(node -> node.path("note").asString()).containsExactlyElementsOf(notes);
			assertThat(after.path("amendments")).allSatisfy(note -> {
				assertThat(note.has("authorUserId")).isFalse();
				assertThat(note.path("createdAt").asString()).isEqualTo("2026-09-28T00:30:00+08:00");
			});
			assertThat(after.path("sections")).isEqualTo(before.path("sections"));
			assertThat(after.path("missingItems")).isEqualTo(before.path("missingItems"));
			assertThat(after.path("dataComplete").booleanValue()).isFalse();
			assertThat(getJson(family, "/api/elders/101/weekly-summary?weekStart=2026-09-21")).isEqualTo(summary);
			assertThat(generate(manager, 101, "2026-09-21", "2026-09-27")).isEqualTo(generated);
			assertThat(jdbc.queryForList("SELECT * FROM report ORDER BY id")).isEqualTo(originalReports);
			assertThat(jdbc.queryForList("SELECT * FROM report_amendment ORDER BY id")).isEqualTo(originalAmendments);
			assertThat(audits()).hasSize(4).extracting(Audit::result).containsOnly("OK");
			assertThat(audits().toString()).doesNotContain("Correction:", "Evidence checked", "Second correction");
		}
	}

	@Test
	void separateFamilySessionsReadOnlyTheirAudienceAndEldersIncludingReadOnlyArchivedReports() throws Exception {
		try (var manager = browser(); var family = browser(); var other = browser()) {
			login(manager, "manager");
			var generated = generate(manager, 101, "2026-09-21", "2026-09-27");
			long ownId = reportId(generated, "FAMILY");
			long archivedId = reportId(generate(manager, 102, "2026-09-21", "2026-09-27"), "FAMILY");
			long otherId = reportId(generate(manager, 110, "2026-09-21", "2026-09-27"), "FAMILY");
			jdbc.update("UPDATE report SET status = 'ARCHIVED' WHERE id = ?", archivedId);
			var originalReports = jdbc.queryForList("SELECT * FROM report ORDER BY id");
			login(family, "family-a");
			login(other, "family-b");
			assertThat(getJson(family, "/api/reports").path("items"))
					.extracting(node -> node.path("id").longValue()).containsExactlyInAnyOrder(ownId, archivedId);
			assertThat(getJson(other, "/api/reports").path("items"))
					.extracting(node -> node.path("id").longValue()).containsExactly(otherId);
			assertThat(getJson(family, "/api/reports/" + archivedId).path("status").asString()).isEqualTo("ARCHIVED");
			assertThat(getJson(family, "/api/elders/102/weekly-summary?weekStart=2026-09-21")
					.path("reportId").longValue()).isEqualTo(archivedId);
			assertThat(getJson(other, "/api/reports/" + otherId).toString()).contains("OTHER-FAMILY-OBSERVATION");
			for (String path : List.of("/api/reports?elderId=110", "/api/reports/" + otherId,
					"/api/elders/110/weekly-summary?weekStart=2026-09-21",
					"/api/reports/" + reportId(generated, "REGULATOR"), "/api/reports/" + reportId(generated, "INTERNAL"))) {
				assertDenied(family, path, 403);
			}
			assertDenied(other, "/api/reports/" + ownId, 403);
			postJson(family, "/api/reports/generate", Map.of("elderId", 101, "periodStart", "2026-09-21", "periodEnd", "2026-09-27"), 403);
			postJson(family, "/api/reports/" + ownId + "/amendments", Map.of("note", "Unauthorized correction"), 403);
			assertThat(jdbc.queryForList("SELECT * FROM report ORDER BY id")).isEqualTo(originalReports);
			assertThat(jdbc.queryForList("SELECT * FROM report_amendment")).isEmpty();
			assertThat(audits()).filteredOn(audit -> audit.result().equals("DENIED")).hasSize(6)
					.extracting(Audit::actor).containsExactly(7L, 7L, 7L, 7L, 7L, 9L);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"REVOKED", "EXPIRED"})
	void anExistingSessionLosesAllReportAccessWhenItsBindingIsRevokedOrReachesExpiry(String change) throws Exception {
		try (var manager = browser(); var family = browser()) {
			login(manager, "manager");
			long id = reportId(generate(manager, 101, "2026-09-21", "2026-09-27"), "FAMILY");
			jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE id = 701", Timestamp.valueOf(NOW.plusSeconds(1)));
			login(family, "family-a");
			String session = cookie(family, "JSESSIONID").getValue();
			getJson(family, "/api/reports/" + id);
			getJson(family, "/api/elders/101/weekly-summary?weekStart=2026-09-21");
			if (change.equals("REVOKED")) {
				jdbc.update("UPDATE elder_family_binding SET status = 'REVOKED' WHERE id = 701");
			} else {
				jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE id = 701", Timestamp.valueOf(NOW));
			}
			for (String path : reportPaths(id)) assertDenied(family, path, 403);
			assertThat(getJson(family, "/api/elders")).extracting(node -> node.path("id").longValue()).containsExactly(102L);
			assertThat(getJson(family, "/api/reports").path("items")).isEmpty();
			assertThat(getJson(family, "/api/auth/me").path("id").longValue()).isEqualTo(7);
			assertThat(cookie(family, "JSESSIONID").getValue()).isEqualTo(session);
			assertThat(audits()).filteredOn(audit -> audit.result().equals("DENIED")).hasSize(3)
					.extracting(Audit::actor).containsOnly(7L);
		}
	}

	@Test
	void logoutInvalidatesOldSessionCookiesAcrossListDetailAndSummary() throws Exception {
		try (var manager = browser(); var family = browser(); var replay = browser(); var fresh = browser()) {
			login(manager, "manager");
			long id = reportId(generate(manager, 101, "2026-09-21", "2026-09-27"), "FAMILY");
			login(family, "family-a");
			getJson(family, "/api/reports/" + id);
			String oldSession = cookie(family, "JSESSIONID").getValue();
			var request = HttpRequest.newBuilder(uri("/api/auth/logout")).timeout(Duration.ofSeconds(10))
					.header("X-XSRF-TOKEN", cookie(family, "XSRF-TOKEN").getValue())
					.POST(HttpRequest.BodyPublishers.noBody()).build();
			assertThat(family.client().send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(204);
			var oldCookie = new HttpCookie("JSESSIONID", oldSession);
			oldCookie.setPath("/");
			oldCookie.setVersion(0);
			replay.cookies().getCookieStore().add(uri("/"), oldCookie);
			for (var client : List.of(family, replay, fresh)) {
				for (String path : reportPaths(id)) assertDenied(client, path, 401);
			}
			assertThat(audits()).hasSize(1);
			login(family, "family-a");
			assertThat(cookie(family, "JSESSIONID").getValue()).isNotEqualTo(oldSession);
			assertThat(getJson(family, "/api/reports/" + id).path("id").longValue()).isEqualTo(id);
			assertThat(audits()).hasSize(2).extracting(Audit::result).containsOnly("OK");
		}
	}

	@Test
	void familyReportReadingPreservesIntakeScheduleAndManagerContracts() throws Exception {
		try (var manager = browser(); var family = browser(); var other = browser()) {
			login(manager, "manager");
			var generated = generate(manager, 101, "2026-09-21", "2026-09-27");
			assertThat(getJson(manager, "/api/reports?elderId=101").path("items")).hasSize(3);
			assertThat(getJson(manager, "/api/elders")).hasSize(3);
			assertThat(audits()).isEmpty();
			login(family, "family-a");
			getJson(family, "/api/reports/" + reportId(generated, "FAMILY"));
			var application = postJson(family, "/api/intake-applications", Map.of("targetElderName", "New elder",
					"targetAddress", "12 Example Road", "postalCode", "123456"), 201);
			assertThat(application.path("applicantFamilyMemberId").longValue()).isEqualTo(42);
			assertThat(application.path("status").asString()).isEqualTo("SUBMITTED");
			assertThat(getJson(family, "/api/intake-applications").path("items")).containsExactly(application);
			assertThat(getJson(family, "/api/intake-applications/" + application.path("id").longValue())).isEqualTo(application);
			var schedule = getJson(family, "/api/visits?elderId=101&dateFrom=2026-09-21&dateTo=2026-09-27");
			assertThat(schedule.path("totalElements").longValue()).isEqualTo(3);
			assertThat(schedule.path("items")).extracting(node -> node.path("id").longValue()).containsExactly(201L, 202L, 203L);
			assertThat(getJson(family, "/api/caregivers/501").path("fullName").asString()).isEqualTo("Caregiver Mei");
			login(other, "family-b");
			assertThat(getJson(other, "/api/intake-applications").path("items")).isEmpty();
			assertDenied(other, "/api/intake-applications/" + application.path("id").longValue(), 403);
			assertThat(getJson(manager, "/api/reports?elderId=101").path("items")).hasSize(3);
			assertThat(audits()).hasSize(3).extracting(Audit::resource).containsExactly("REPORT", "VISIT", "CAREGIVER");
		}
	}

	private List<String> reportPaths(long id) {
		return List.of("/api/reports?elderId=101", "/api/reports/" + id,
				"/api/elders/101/weekly-summary?weekStart=2026-09-21");
	}

	private void assertDenied(Browser browser, String path, int status) throws Exception {
		var response = get(browser, path);
		assertThat(response.statusCode()).as("GET %s: %s", path, response.body()).isEqualTo(status);
		assertThat(response.body()).doesNotContain("Walked two laps", "OTHER-FAMILY", "Systolic", "Slipped", "Caregiver Mei", "New elder");
	}

	private void visit(long id, long elderId, String start, String status) {
		jdbc.update("INSERT INTO visit (id, elder_id, caregiver_id, service_type, scheduled_start, status) VALUES (?, ?, 501, 'Personal care', ?, ?)", id, elderId, at(start), status);
	}

	private static Timestamp at(String value) {
		return Timestamp.valueOf(LocalDateTime.parse(value));
	}

	private JsonNode generate(Browser manager, long elderId, String start, String end) throws Exception {
		return postJson(manager, "/api/reports/generate", Map.of("elderId", elderId, "periodStart", start, "periodEnd", end), 202);
	}

	private long reportId(JsonNode reports, String audience) {
		for (var report : reports) {
			if (report.path("audience").asString().equals(audience)) return report.path("id").longValue();
		}
		throw new AssertionError("Missing generated " + audience + " report");
	}

	private String section(JsonNode report, String title) {
		for (var section : report.path("sections")) {
			if (section.path("title").asString().equals(title)) return section.path("body").asString();
		}
		throw new AssertionError("Missing section " + title);
	}

	private Browser browser() {
		var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		return new Browser(HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build(), cookies);
	}

	private void login(Browser browser, String username) throws Exception {
		assertThat(get(browser, "/api/auth/csrf").statusCode()).isEqualTo(200);
		assertThat(cookie(browser, "XSRF-TOKEN").isHttpOnly()).isFalse();
		assertThat(postJson(browser, "/api/auth/login", Map.of("username", username, "password", "test-password"), 200)
				.path("username").asString()).isEqualTo(username);
		assertThat(cookie(browser, "JSESSIONID").isHttpOnly()).isTrue();
	}

	private HttpCookie cookie(Browser browser, String name) {
		return browser.cookies().getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals(name))
				.findFirst().orElseThrow(() -> new AssertionError("Missing response cookie: " + name));
	}

	private JsonNode postJson(Browser browser, String path, Object body, int expectedStatus) throws Exception {
		var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json").header("X-XSRF-TOKEN", cookie(browser, "XSRF-TOKEN").getValue())
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
		var response = browser.client().send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as("POST %s: %s", path, response.body()).isEqualTo(expectedStatus);
		return json.readTree(response.body());
	}

	private JsonNode getJson(Browser browser, String path) throws Exception {
		var response = get(browser, path);
		assertThat(response.statusCode()).as("GET %s: %s", path, response.body()).isEqualTo(200);
		return json.readTree(response.body());
	}

	private HttpResponse<String> get(Browser browser, String path) throws Exception {
		return browser.client().send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://127.0.0.1:" + port + path);
	}

	private List<Audit> audits() {
		return jdbc.query("SELECT actor_user_id, resource_type, result, detail FROM audit_log WHERE action = 'READ' ORDER BY id",
				(row, index) -> new Audit(row.getLong("actor_user_id"), row.getString("resource_type"), row.getString("result"), row.getString("detail")));
	}

	private record Browser(HttpClient client, CookieManager cookies) implements AutoCloseable {
		@Override
		public void close() { client.close(); }
	}

	private record Audit(long actor, String resource, String result, String detail) {
	}
}
