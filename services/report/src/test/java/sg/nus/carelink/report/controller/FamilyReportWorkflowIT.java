package sg.nus.carelink.report.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.sql.Timestamp;
import java.time.Clock;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.report.support.SeededFamilyAccess;
import sg.nus.carelink.report.support.SignedInSessions;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * MG07 generation to FM04 reading through report's API and isolated MySQL. Care facts and active
 * bindings are fixtures; report content is generated through the API. Each browser holds the
 * session core leaves after sign-in, and a {@code CoreApi} double answers family access from the
 * seeded bindings ({@link SeededFamilyAccess}).
 *
 * <p>Signing in and out, the elder list, intake applications and the visit schedule belong to core
 * and visit, and are tested there.
 *
 * @author Wang Zhili
 */
@SpringBootTest(properties = "carelink.report.schedule-cron=-")
@AutoConfigureMockMvc
@Import(FamilyReportWorkflowIT.FixedTime.class)
class FamilyReportWorkflowIT {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 0, 30);
	private static final String DISCLAIMER = "This summary records care observations and services; it is not a diagnosis or medical advice.";
	private static final String OBSERVATION = "Walked two laps.\n\n  Follow-up: rest after activity.";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyReportWorkflowIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private Clock clock;
	/** core, which answers report's questions about family access */
	@MockitoBean
	private CoreApi core;
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
		SeededFamilyAccess.answer(core, jdbc, clock);
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
			assertThat(detail.toString()).doesNotContain("Manager Private", "INTERNAL-ONLY", "basisId", "metrics", "authorUserId", "OTHER-FAMILY");
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
			assertThat(audits).hasSize(4).extracting(Audit::actor).containsOnly(7L);
			assertThat(audits).extracting(Audit::result).containsOnly("OK");
			assertThat(audits).extracting(Audit::resource).containsExactly("REPORT", "REPORT", "REPORT", "ELDER");
			assertThat(audits.get(0).detail()).contains("FM04_LIST_REPORTS;", "page=0", "size=1");
			assertThat(audits.get(3).detail()).contains("FM04_READ_WEEKLY_SUMMARY;", "weekStart=2026-09-21");
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
			getJson(family, "/api/reports/" + id);
			getJson(family, "/api/elders/101/weekly-summary?weekStart=2026-09-21");
			if (change.equals("REVOKED")) {
				jdbc.update("UPDATE elder_family_binding SET status = 'REVOKED' WHERE id = 701");
			} else {
				jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE id = 701", Timestamp.valueOf(NOW));
			}
			for (String path : reportPaths(id)) assertDenied(family, path, 403);
			assertThat(getJson(family, "/api/reports").path("items")).isEmpty();
			assertThat(audits()).filteredOn(audit -> audit.result().equals("DENIED")).hasSize(3)
					.extracting(Audit::actor).containsOnly(7L);
		}
	}

	private List<String> reportPaths(long id) {
		return List.of("/api/reports?elderId=101", "/api/reports/" + id,
				"/api/elders/101/weekly-summary?weekStart=2026-09-21");
	}

	private void assertDenied(Browser browser, String path, int status) throws Exception {
		var response = read(browser, path);
		assertThat(response.getStatus()).as("GET %s: %s", path, response.getContentAsString()).isEqualTo(status);
		assertThat(response.getContentAsString()).doesNotContain("Walked two laps", "OTHER-FAMILY", "Systolic", "Slipped", "Caregiver Mei", "New elder");
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
		return new Browser();
	}

	/** Signs in through core: the browser now holds the session core leaves behind. */
	private void login(Browser browser, String username) {
		browser.session = new SignedInSessions(jdbc).of(username);
	}

	private JsonNode postJson(Browser browser, String path, Object body, int expectedStatus) throws Exception {
		var response = mvc.perform(post(path).session(browser.session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
				.andReturn().getResponse();
		assertThat(response.getStatus()).as("POST %s: %s", path, response.getContentAsString()).isEqualTo(expectedStatus);
		return json.readTree(response.getContentAsString());
	}

	private JsonNode getJson(Browser browser, String path) throws Exception {
		var response = read(browser, path);
		assertThat(response.getStatus()).as("GET %s: %s", path, response.getContentAsString()).isEqualTo(200);
		return json.readTree(response.getContentAsString());
	}

	private MockHttpServletResponse read(Browser browser, String path) throws Exception {
		return mvc.perform(get(path).session(browser.session)).andReturn().getResponse();
	}

	private List<Audit> audits() {
		return jdbc.query("SELECT actor_user_id, resource_type, result, detail FROM audit_log WHERE action = 'READ' ORDER BY id",
				(row, index) -> new Audit(row.getLong("actor_user_id"), row.getString("resource_type"), row.getString("result"), row.getString("detail")));
	}

	/** One person's browser: the session they signed in with, as core left it. */
	private static final class Browser implements AutoCloseable {
		private MockHttpSession session = new MockHttpSession();

		@Override
		public void close() {
			session.invalidate();
		}
	}

	private record Audit(long actor, String resource, String result, String detail) {
	}
}
