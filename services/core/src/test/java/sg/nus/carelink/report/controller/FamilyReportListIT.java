package sg.nus.carelink.report.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

import sg.nus.carelink.report.application.ReportService;
import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * Family report listing through real sessions, authorization, audit and MySQL queries.
 *
 * @author Wang Zhili
 */
@SpringBootTest(properties = {"report.schedule-cron=-", "escalation.scan-initial-delay=PT1H"})
@AutoConfigureMockMvc
@Import(FamilyReportListIT.FixedTime.class)
class FamilyReportListIT {

	private static final String PATH = "/api/reports";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 0, 30);
	private static final String CONTENT = """
			{"sections":[{"title":"Observations","body":"Report body excluded from list"}],
			 "dataComplete":false,"missingItems":["Visit 201 on 2026-09-22 not closed"],
			 "disclaimer":null,"generatedBy":"TEMPLATE"}
			""";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyReportListIT.class, "+05:00");
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ReportService reports;
	private final JsonMapper json = JsonMapper.builder().build();

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTime {
		@Bean
		@Primary
		Clock familyReportClock() {
			return Clock.fixed(Instant.parse("2026-09-27T16:30:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void prepareIsolatedReports() {
		jdbc.update("DELETE FROM audit_log");
		jdbc.update("DELETE FROM report_amendment");
		jdbc.update("DELETE FROM report");
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
		for (long id : List.of(101L, 102L, 103L, 104L, 105L, 106L, 107L, 110L)) {
			jdbc.update("INSERT INTO elder (id, full_name) VALUES (?, ?)", id, "Test elder " + id);
		}
		seedBinding(101, 42, "FULL", "ACTIVE", null);
		seedBinding(102, 42, "READ_ONLY", "ACTIVE", NOW.plusSeconds(1));
		seedBinding(103, 42, "FULL", "ACTIVE", NOW);
		seedBinding(104, 42, "FULL", "ACTIVE", NOW.minusSeconds(1));
		seedBinding(105, 42, "FULL", "PENDING_CONFIRMATION", null);
		seedBinding(106, 42, "FULL", "REJECTED", null);
		seedBinding(107, 42, "FULL", "REVOKED", null);
		seedBinding(110, 7, "FULL", "ACTIVE", null);
		seedReport(301, 101, "FAMILY", "PUBLISHED", "2026-09-21", NOW);
		seedReport(302, 102, "FAMILY", "ARCHIVED", "2026-09-21", NOW.plusMinutes(1));
		seedReport(303, 101, "FAMILY", "PUBLISHED", "2026-09-21", NOW.plusMinutes(1));
		seedReport(304, 101, "FAMILY", "PUBLISHED", "2026-09-14", NOW.plusDays(1));
		seedReport(310, 110, "FAMILY", "PUBLISHED", "2026-09-28", NOW);
		seedReport(320, 101, "FAMILY", "DRAFT", "2026-09-28", NOW);
		seedReport(321, 101, "INTERNAL", "PUBLISHED", "2026-09-28", NOW);
		seedReport(322, 101, "REGULATOR", "PUBLISHED", "2026-09-28", NOW);
		for (long id : List.of(103L, 104L, 105L, 106L, 107L)) {
			seedReport(id + 300, id, "FAMILY", "PUBLISHED", "2026-09-28", NOW);
		}
		jdbc.update("""
				INSERT INTO report_amendment (report_id, note, author_user_id, created_at)
				VALUES (301, 'Correction excluded from list', 10, '2026-09-28 01:00:00')
				""");
	}

	@Test
	void listsOnlyReadableFamilyReportsUsingExplicitMetadataAndSingaporeTimestamps() throws Exception {
		var body = readPage(loginAs("family-a"));
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("items", "page", "size", "totalElements");
		assertPage(body, 0, 20, 4, 303L, 302L, 301L, 304L);
		assertThat(body.path("items")).allSatisfy(item -> {
			assertThat(item.propertyNames()).containsExactlyInAnyOrder("id", "elderId", "audience", "periodStart",
					"periodEnd", "status", "dataComplete", "missingItems", "generatedBy", "createdAt", "archivedAt");
			assertThat(item.path("audience").asString()).isEqualTo("FAMILY");
			assertThat(item.path("generatedBy").asString()).isEqualTo("TEMPLATE");
			assertThat(item.path("dataComplete").booleanValue()).isFalse();
			assertThat(item.path("missingItems")).extracting(JsonNode::asString)
					.containsExactly("Visit 201 on 2026-09-22 not closed");
			assertThat(item.path("archivedAt").isNull()).isTrue();
		});
		assertThat(body.path("items").get(2).path("createdAt").asString()).isEqualTo("2026-09-28T00:30:00+08:00");
		assertThat(body.path("items").get(1).path("status").asString()).isEqualTo("ARCHIVED");
		assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+05:00");
	}

	@Test
	void unfamiliarLegacyMissingItemsUseASafeNoticeWithoutChangingTheOriginal() throws Exception {
		String stored = CONTENT.replace("\"Visit 201 on 2026-09-22 not closed\"",
				"\"Visit 201 on 2026-09-22 not closed\",\"Internal staff performance details\"");
		jdbc.update("UPDATE report SET content = ? WHERE id = 301", stored);
		var original = jdbc.queryForObject("SELECT content FROM report WHERE id = 301", String.class);
		var family = readPage(loginAs("family-a"), "elderId", "101").path("items").get(1);
		assertThat(family.path("missingItems")).extracting(JsonNode::asString)
				.containsExactly("Visit 201 on 2026-09-22 not closed", "Some care records are incomplete.");
		var manager = readPage(loginAs("manager"), "elderId", "101", "audience", "FAMILY").path("items").get(2);
		assertThat(manager.path("missingItems")).extracting(JsonNode::asString)
				.containsExactly("Visit 201 on 2026-09-22 not closed", "Internal staff performance details");
		assertThat(jdbc.queryForObject("SELECT content FROM report WHERE id = 301", String.class)).isEqualTo(original);
	}

	@Test
	void identityComesFromSessionAndFiltersRunBeforePagingAndCounting() throws Exception {
		assertPage(readPage(loginAs("family-b"), "familyMemberId", "42", "userId", "7", "role", "MANAGER"),
				0, 20, 1, 310L);
		var session = loginAs("family-a");
		assertPage(readPage(session, "size", "2"), 0, 2, 4, 303L, 302L);
		assertPage(readPage(session, "page", "1", "size", "2"), 1, 2, 4, 301L, 304L);
		assertPage(readPage(session, "page", "2", "size", "2"), 2, 2, 4);
		assertPage(readPage(session, "elderId", "101", "audience", "FAMILY", "size", "1"), 0, 1, 3, 303L);
		assertPage(readPage(session, "elderId", "102"), 0, 20, 1, 302L);
	}

	@ParameterizedTest
	@ValueSource(longs = {103, 104, 105, 106, 107, 110, 999})
	void inaccessibleEldersAreForbidden(long elderId) throws Exception {
		mvc.perform(get(PATH).session(loginAs("family-a")).param("elderId", Long.toString(elderId)))
				.andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = {"INTERNAL", "REGULATOR"})
	void otherAudiencesAreForbidden(String audience) throws Exception {
		mvc.perform(get(PATH).session(loginAs("family-a")).param("audience", audience))
				.andExpect(status().isForbidden());
	}

	@Test
	void noBindingsReturnsAnEmptyPageButCannotSelectAnElder() throws Exception {
		var session = loginAs("no-binding");
		assertPage(readPage(session, "page", "2", "size", "3"), 2, 3, 0);
		mvc.perform(get(PATH).session(session).param("elderId", "101")).andExpect(status().isForbidden());
	}

	@Test
	void revocationAndExpiryApplyToTheNextReadInTheSameSession() throws Exception {
		var session = loginAs("family-a");
		assertPage(readPage(session), 0, 20, 4, 303L, 302L, 301L, 304L);
		jdbc.update("UPDATE elder_family_binding SET status = 'REVOKED' WHERE elder_id = 101");
		jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 102", NOW);
		assertPage(readPage(session), 0, 20, 0);
		mvc.perform(get(PATH).session(session).param("elderId", "101")).andExpect(status().isForbidden());
		mvc.perform(get(PATH).session(session).param("elderId", "102")).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = {"no-profile", "caregiver", "elder"})
	void unsupportedAccountsCannotReadReports(String username) throws Exception {
		mvc.perform(get(PATH).session(loginAs(username))).andExpect(status().isForbidden());
	}

	@Test
	void anonymousRequestIsUnauthorizedAndDisabledAccountLosesAccess() throws Exception {
		mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
		var session = loginAs("family-a");
		jdbc.update("UPDATE app_user SET enabled = false WHERE id = 7");
		mvc.perform(get(PATH).session(session)).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@CsvSource({"page,-1", "size,0", "size,201", "page,abc", "page,2147483648", "size,abc",
			"audience,UNKNOWN", "elderId,abc"})
	void invalidFamilyParametersReturnBadRequest(String parameter, String value) throws Exception {
		mvc.perform(get(PATH).session(loginAs("family-a")).param(parameter, value))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void veryLargeValidPageReturnsAnEmptyPageWithTheFilteredTotal() throws Exception {
		assertPage(readPage(loginAs("family-a"), "page", "2147483647", "size", "200"), Integer.MAX_VALUE, 200, 4);
	}

	@Test
	void managerKeepsItsAudienceRulesOrderingAndPaginationClamping() throws Exception {
		var session = loginAs("manager");
		var body = readPage(session);
		assertPage(body, 0, 20, 13, 320L, 403L, 404L, 405L, 406L, 407L, 310L, 322L, 321L, 303L, 301L, 302L, 304L);
		assertThat(body.path("items").get(0).path("createdAt").asString()).isEqualTo("2026-09-28T00:30:00");
		assertPage(readPage(session, "page", "-1", "size", "0"), 0, 1, 13, 320L);
		assertPage(readPage(session, "audience", "INTERNAL"), 0, 20, 1, 321L);
		assertPage(readPage(session, "elderId", "101", "audience", "FAMILY"), 0, 20, 4, 320L, 303L, 301L, 304L);
		mvc.perform(get(PATH + "/301").session(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.sections[0].body").value("Report body excluded from list"))
				.andExpect(jsonPath("$.amendments[0].authorUserId").value(10));
	}

	@Test
	void writeEndpointsRemainManagerOnly() throws Exception {
		var session = loginAs("family-a");
		Cookie token = mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		mvc.perform(post(PATH + "/generate").session(session).cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"elderId\":101,\"periodStart\":\"2026-09-21\",\"periodEnd\":\"2026-09-27\"}"))
				.andExpect(status().isForbidden());
		mvc.perform(post(PATH + "/301/amendments").session(session).cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Family cannot amend\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void readsTheFamilyVersionGeneratedByMg07WithoutChangingTheStoredReports() throws Exception {
		var generated = reports.generate(101L, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13), 10L);
		assertThat(generated).hasSize(3);
		long familyId = generated.stream().filter(report -> report.audience() == Report.Audience.FAMILY).findFirst().orElseThrow().id();
		var before = jdbc.queryForList("SELECT * FROM report ORDER BY id");
		var body = readPage(loginAs("family-a"), "elderId", "101");
		assertPage(body, 0, 20, 4, 303L, 301L, 304L, familyId);
		assertThat(body.path("items").get(3).path("dataComplete").booleanValue()).isTrue();
		assertThat(body.path("items").get(3).path("missingItems")).isEmpty();
		assertThat(jdbc.queryForList("SELECT * FROM report ORDER BY id")).isEqualTo(before);
	}

	@Test
	void auditRecordsSuccessDenialAndFailureWithoutStoringReportContent() throws Exception {
		var session = loginAs("family-a");
		readPage(session, "elderId", "101", "size", "1");
		mvc.perform(get(PATH).session(session).param("elderId", "110")).andExpect(status().isForbidden());
		jdbc.update("UPDATE report SET content = NULL WHERE id = 301");
		mvc.perform(get(PATH).session(session)).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.items").doesNotExist());
		var entries = jdbc.queryForList("SELECT * FROM audit_log WHERE resource_type = 'REPORT' ORDER BY id");
		assertThat(entries).extracting(entry -> entry.get("result")).containsExactly("OK", "DENIED", "FAILED");
		assertThat(entries).allSatisfy(entry -> {
			assertThat(entry.get("actor_user_id")).isEqualTo(7L);
			assertThat(entry.get("action")).isEqualTo("READ");
			assertThat(entry.get("resource_id")).isNull();
			assertThat(entry.get("occurred_at")).isNotNull();
		});
		assertThat(entries.getFirst().get("detail")).isEqualTo("FM04_LIST_REPORTS;elderId=101;audience=FAMILY;page=0;size=1");
		assertThat(entries.get(1).get("detail")).isEqualTo("FM04_LIST_REPORTS;elderId=110;audience=FAMILY;page=0;size=20");
		assertThat(entries.get(2).get("detail")).isEqualTo("FM04_LIST_REPORTS;elderId=null;audience=FAMILY;page=0;size=20");
	}

	@Test
	void auditStorageFailureReturns503WithoutProtectedContent() throws Exception {
		var session = loginAs("family-a");
		jdbc.execute("RENAME TABLE audit_log TO fm04_audit_log_unavailable");
		try {
			mvc.perform(get(PATH).session(session)).andExpect(status().isServiceUnavailable())
					.andExpect(jsonPath("$.items").doesNotExist());
		} finally {
			jdbc.execute("RENAME TABLE fm04_audit_log_unavailable TO audit_log");
		}
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

	private void seedReport(long id, long elderId, String audience, String reportStatus, String start, LocalDateTime createdAt) {
		LocalDate periodStart = LocalDate.parse(start);
		jdbc.update("""
				INSERT INTO report (id, elder_id, generated_by_user_id, audience, period_start, period_end, status, content, created_at)
				VALUES (?, ?, 10, ?, ?, ?, ?, ?, ?)
				""", id, elderId, audience, periodStart, periodStart.plusDays(6), reportStatus, CONTENT, createdAt);
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
				.content(json.writeValueAsString(Map.of("username", username, "password", "test-password"))))
				.andExpect(status().isOk()).andReturn();
		var session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session).isNotNull();
		return session;
	}
}
