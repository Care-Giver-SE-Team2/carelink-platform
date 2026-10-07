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
 * Family report details through real login sessions, current bindings, MySQL and auditing.
 *
 * @author Wang Zhili
 */
@SpringBootTest(properties = {"carelink.report.schedule-cron=-", "carelink.escalation.scan-initial-delay=PT1H"})
@AutoConfigureMockMvc
@Import(FamilyReportDetailIT.FixedTime.class)
class FamilyReportDetailIT {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 0, 30);
	private static final String DISCLAIMER = "This summary records care observations and services; it is not a diagnosis or medical advice.";
	private static final String CONTENT = """
			{"sections":[],"dataComplete":true,"missingItems":[],"disclaimer":null,"generatedBy":"TEMPLATE"}
			""";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyReportDetailIT.class, "+05:00");
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
		Clock familyDetailClock() {
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

	@ParameterizedTest
	@ValueSource(longs = {301, 302})
	void readsPublishedAndArchivedReportsWithFamilyMetadataAndFixedDisclaimer(long id) throws Exception {
		var body = readDetail(loginAs("family-a"), id);
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("id", "elderId", "audience", "periodStart", "periodEnd",
				"status", "dataComplete", "missingItems", "generatedBy", "createdAt", "archivedAt", "sections", "disclaimer", "amendments");
		assertThat(body.path("id").longValue()).isEqualTo(id);
		assertThat(body.path("audience").asString()).isEqualTo("FAMILY");
		assertThat(body.path("status").asString()).isEqualTo(id == 301 ? "PUBLISHED" : "ARCHIVED");
		assertThat(body.path("createdAt").asString()).isEqualTo("2026-09-28T00:30:00+08:00");
		assertThat(body.path("archivedAt").isNull()).isTrue();
		assertThat(body.path("disclaimer").asString()).isEqualTo(DISCLAIMER);
		assertThat(body.path("sections")).isEmpty();
		assertThat(body.path("amendments")).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(longs = {310, 320, 321, 322})
	void knownForbiddenReportsReturn403EvenWhenTheirContentIsAbsent(long id) throws Exception {
		jdbc.update("UPDATE report SET content = NULL WHERE id = ?", id);
		mvc.perform(get("/api/reports/{id}", id).session(loginAs("family-a")))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.sections").doesNotExist());
	}

	@Test
	void unknownReportIs404AndCannotBeSelectedUsingClientIdentityOrRole() throws Exception {
		var session = loginAs("family-b");
		mvc.perform(get("/api/reports/999").session(session)).andExpect(status().isNotFound());
		mvc.perform(get("/api/reports/301").session(session).param("familyMemberId", "42")
				.param("userId", "7").param("role", "MANAGER").param("elderId", "110").param("projection", "internal"))
				.andExpect(status().isForbidden());
		assertThat(readDetail(session, 310).path("elderId").longValue()).isEqualTo(110L);
	}

	@ParameterizedTest
	@ValueSource(strings = {"no-profile", "no-binding", "caregiver", "elder"})
	void unsupportedAccountsCannotReadDetails(String username) throws Exception {
		mvc.perform(get("/api/reports/301").session(loginAs(username))).andExpect(status().isForbidden());
	}

	@Test
	void anonymousAndDisabledAccountsCannotReadDetails() throws Exception {
		mvc.perform(get("/api/reports/301")).andExpect(status().isUnauthorized());
		var session = loginAs("family-a");
		jdbc.update("UPDATE app_user SET enabled = false WHERE id = 7");
		mvc.perform(get("/api/reports/301").session(session)).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = {"REVOKED", "REJECTED", "PENDING_CONFIRMATION"})
	void bindingStatusIsCheckedAgainInTheSameSession(String bindingStatus) throws Exception {
		var session = loginAs("family-a");
		readDetail(session, 301);
		jdbc.update("UPDATE elder_family_binding SET status = ? WHERE elder_id = 101", bindingStatus);
		mvc.perform(get("/api/reports/301").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void readOnlyBindingExpiresAtItsExactSingaporeTime() throws Exception {
		var session = loginAs("family-a");
		jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 102", NOW.plusSeconds(1));
		readDetail(session, 302);
		jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 102", NOW);
		mvc.perform(get("/api/reports/302").session(session)).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "9223372036854775808"})
	void malformedIdsReturn400(String id) throws Exception {
		mvc.perform(get("/api/reports/{id}", id).session(loginAs("family-a"))).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = {"MODEL", "TEMPLATE"})
	void eitherStoredGenerationSourceIsReadableWithoutRegeneration(String source) throws Exception {
		jdbc.update("UPDATE report SET content = ? WHERE id = 301", CONTENT.replace("TEMPLATE", source));
		var before = jdbc.queryForList("SELECT * FROM report ORDER BY id");
		assertThat(readDetail(loginAs("family-a"), 301).path("generatedBy").asString()).isEqualTo(source);
		assertThat(jdbc.queryForList("SELECT * FROM report ORDER BY id")).isEqualTo(before);
	}

	@Test
	void managerRetainsOriginalContentAndInternalAmendmentAuthor() throws Exception {
		jdbc.update("""
				INSERT INTO report_amendment (id, report_id, note, author_user_id, created_at)
				VALUES (501, 301, 'Manager correction', 10, '2026-09-28 01:00:00')
				""");
		var body = readDetail(loginAs("manager"), 301);
		assertThat(body.path("disclaimer").isNull()).isTrue();
		assertThat(body.path("createdAt").asString()).isEqualTo("2026-09-28T00:30:00");
		assertThat(body.path("amendments").get(0).path("authorUserId").longValue()).isEqualTo(10);
		assertThat(readDetail(loginAs("manager"), 321).path("audience").asString()).isEqualTo("INTERNAL");
	}

	@Test
	void readsStoredFamilyTextAndOrderedCorrectionsWithoutChangingTheArchive() throws Exception {
		String observation = "Caregiver Mei: walked two laps.\nRecorded text: <b>steady</b>; BP < 140.";
		var sections = List.of(
				Map.of("title", "Service completion", "body", "Two visits completed by Mei."),
				Map.of("title", "Staff performance", "body", "Internal assessment"),
				Map.of("title", "Vital signs", "body", "Systolic 128–142 mmHg"),
				Map.of("title", "Observations", "body", observation),
				Map.of("title", "Incidents", "body", "A fall was reported; follow-up completed."));
		jdbc.update("UPDATE report SET content = ? WHERE id = 301", json.writeValueAsString(Map.of(
				"sections", sections, "dataComplete", false,
				"missingItems", List.of("Visit 201 on 2026-09-22 not closed", "Internal legacy gap"),
				"disclaimer", "Archived disclaimer", "generatedBy", "TEMPLATE")));
		jdbc.update("""
				INSERT INTO report_amendment (id, report_id, note, author_user_id, created_at) VALUES
				(500, 301, 'Later correction', 10, '2026-09-28 02:00:00'),
				(502, 301, 'Second correction', 10, '2026-09-28 01:00:00'),
				(501, 301, 'First correction: three laps, not two.', 10, '2026-09-28 01:00:00'),
				(510, 321, 'Internal report correction', 10, '2026-09-28 01:00:00')
				""");
		var manager = loginAs("manager");
		var before = readDetail(manager, 301);
		var body = readDetail(loginAs("family-a"), 301);
		assertThat(body.path("sections")).extracting(section -> section.path("title").asString())
				.containsExactly("Service completion", "Vital signs", "Observations", "Incidents");
		assertThat(body.path("sections")).allSatisfy(section ->
				assertThat(section.propertyNames()).containsExactlyInAnyOrder("title", "body"));
		assertThat(body.path("sections").get(0).path("body").asString()).isEqualTo("Two visits completed by Mei.");
		assertThat(body.path("sections").get(1).path("body").asString()).isEqualTo("Systolic 128–142 mmHg");
		assertThat(body.path("sections").get(2).path("body").asString()).isEqualTo(observation);
		assertThat(body.path("sections").get(3).path("body").asString()).isEqualTo("A fall was reported; follow-up completed.");
		assertThat(body.path("dataComplete").booleanValue()).isFalse();
		assertThat(body.path("missingItems")).extracting(JsonNode::asString)
				.containsExactly("Visit 201 on 2026-09-22 not closed", "Some care records are incomplete.");
		assertThat(body.path("disclaimer").asString()).isEqualTo(DISCLAIMER);
		assertThat(body.path("amendments")).extracting(note -> note.path("id").longValue()).containsExactly(501L, 502L, 500L);
		assertThat(body.path("amendments")).allSatisfy(note ->
				assertThat(note.propertyNames()).containsExactlyInAnyOrder("id", "note", "createdAt"));
		assertThat(body.path("amendments").get(0).path("note").asString()).isEqualTo("First correction: three laps, not two.");
		assertThat(body.path("amendments").get(0).path("createdAt").asString()).isEqualTo("2026-09-28T01:00:00+08:00");
		assertThat(readDetail(manager, 301)).isEqualTo(before);
	}

	@Test
	void managerGeneratedFamilyReportAndItsNewCorrectionAreReadableInTheSameSession() throws Exception {
		jdbc.update("INSERT INTO caregiver (id, user_id, full_name) VALUES (201, 14, 'Mei')");
		jdbc.update("""
				INSERT INTO visit (id, elder_id, caregiver_id, service_type, scheduled_start, status) VALUES
				(701, 101, 201, 'Personal care', '2026-09-14 09:00:00', 'VERIFIED'),
				(702, 101, 201, 'Personal care', '2026-09-16 09:00:00', 'VERIFIED')
				""");
		jdbc.update("""
				INSERT INTO vital_sign (visit_id, metric, value, unit, out_of_range, recorded_at) VALUES
				(701, 'systolic', 128, 'mmHg', false, '2026-09-14 09:10:00'),
				(702, 'systolic', 142, 'mmHg', true, '2026-09-16 09:10:00')
				""");
		jdbc.update("""
				INSERT INTO visit_task (visit_id, name, status, caregiver_note)
				VALUES (701, 'Mobility', 'DONE', 'Walked two laps with a cane.')
				""");
		var manager = loginAs("manager");
		var generated = managerPost(manager, "/api/reports/generate",
				Map.of("elderId", 101, "periodStart", "2026-09-14", "periodEnd", "2026-09-20"), 202);
		assertThat(generated).hasSize(3);
		long familyId = 0;
		long internalId = 0;
		for (JsonNode report : generated) {
			if (report.path("audience").asString().equals("FAMILY")) {
				familyId = report.path("id").longValue();
			} else if (report.path("audience").asString().equals("INTERNAL")) {
				internalId = report.path("id").longValue();
			}
		}
		assertThat(familyId).isPositive();
		assertThat(internalId).isPositive();
		var session = loginAs("family-a");
		var original = readDetail(session, familyId);
		assertThat(original.path("sections")).extracting(section -> section.path("title").asString())
				.containsExactly("Service completion", "Vital signs", "Observations", "Incidents");
		assertThat(original.path("sections").get(0).path("body").asString()).contains("Mei").doesNotContain("#201");
		assertThat(original.path("sections").get(1).path("body").asString()).isEqualTo("Systolic 128–142 mmHg");
		assertThat(original.path("sections").get(2).path("body").asString()).contains("Mei: Walked two laps with a cane.");
		assertThat(original.path("disclaimer").asString()).isEqualTo(DISCLAIMER);
		assertThat(original.path("generatedBy").asString()).isEqualTo("TEMPLATE");
		assertThat(original.path("amendments")).isEmpty();
		mvc.perform(get("/api/reports/{id}", internalId).session(session)).andExpect(status().isForbidden());
		var amendment = managerPost(manager, "/api/reports/" + familyId + "/amendments",
				Map.of("note", "Correction: three laps were completed."), 201);
		var corrected = readDetail(session, familyId);
		assertThat(corrected.path("sections")).isEqualTo(original.path("sections"));
		assertThat(corrected.path("amendments")).hasSize(1);
		assertThat(corrected.path("amendments").get(0).path("id")).isEqualTo(amendment.path("id"));
		assertThat(corrected.path("amendments").get(0).path("note").asString()).isEqualTo("Correction: three laps were completed.");
		assertThat(corrected.path("amendments").get(0).has("authorUserId")).isFalse();
		assertThat(readDetail(manager, familyId).path("amendments").get(0).path("authorUserId").longValue()).isEqualTo(10L);
	}

	@Test
	void auditRecordsTheAccountReportAndOutcomeWithoutReportText() throws Exception {
		var session = loginAs("family-a");
		readDetail(session, 301);
		mvc.perform(get("/api/reports/310").session(session)).andExpect(status().isForbidden());
		mvc.perform(get("/api/reports/999").session(session)).andExpect(status().isNotFound());
		jdbc.update("UPDATE report SET content = NULL WHERE id = 301");
		mvc.perform(get("/api/reports/301").session(session)).andExpect(status().isInternalServerError());
		var entries = jdbc.queryForList("SELECT * FROM audit_log WHERE resource_type = 'REPORT' ORDER BY id");
		assertThat(entries).extracting(entry -> entry.get("result")).containsExactly("OK", "DENIED", "FAILED", "FAILED");
		assertThat(entries).extracting(entry -> entry.get("resource_id")).containsExactly(301L, 310L, 999L, 301L);
		assertThat(entries).allSatisfy(entry -> {
			assertThat(entry.get("actor_user_id")).isEqualTo(7L);
			assertThat(entry.get("action")).isEqualTo("READ");
			assertThat(entry.get("detail")).isEqualTo("FM04_READ_REPORT");
			assertThat(entry.get("occurred_at")).isNotNull();
		});
	}

	@Test
	void auditOutageReturns503WithoutContent() throws Exception {
		var session = loginAs("family-a");
		jdbc.execute("RENAME TABLE audit_log TO fm04_detail_audit_unavailable");
		try {
			for (long id : List.of(301L, 310L, 999L)) {
				mvc.perform(get("/api/reports/{id}", id).session(session)).andExpect(status().isServiceUnavailable())
						.andExpect(jsonPath("$.sections").doesNotExist());
			}
		} finally {
			jdbc.execute("RENAME TABLE fm04_detail_audit_unavailable TO audit_log");
		}
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
