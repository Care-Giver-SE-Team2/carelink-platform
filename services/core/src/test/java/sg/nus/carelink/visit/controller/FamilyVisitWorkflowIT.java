package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * Real HTTP/Session/CSRF and MySQL acceptance of FM03 reads.
 * SQL simulates upstream facts, not CG03/CG05 write operations.
 * @author Wang Zhili
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {"carelink.report.schedule-cron=-", "carelink.escalation.scan-initial-delay=PT1H"})
@Import(FamilyVisitWorkflowIT.FixedTime.class)
class FamilyVisitWorkflowIT {
	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyVisitWorkflowIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
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
		Clock familyVisitWorkflowClock() {
			return Clock.fixed(Instant.parse("2026-09-30T01:20:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void prepareIsolatedWorkflow() {
		for (String table : List.of("audit_log", "visit_task", "visit_state_transition", "visit", "caregiver",
				"elder_family_binding", "elder", "family_member", "user_role", "app_user")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("""
				INSERT INTO app_user (id,username,password_hash,display_name) VALUES
				(10,'manager','{noop}test-password','Manager'),(7,'family-a','{noop}test-password','Family A'),(9,'family-b','{noop}test-password','Family B'),(14,'caregiver','{noop}test-password','Caregiver Mei')
				""");
		jdbc.update("""
				INSERT INTO user_role (user_id,role) VALUES (10,'MANAGER'),(7,'FAMILY'),(9,'FAMILY'),(14,'CAREGIVER')
				""");
		jdbc.update("""
				INSERT INTO family_member (id,user_id,full_name) VALUES (42,7,'Family A'),(7,9,'Family B')
				""");
		jdbc.update("""
				INSERT INTO elder (id,full_name) VALUES (101,'Elder Tan'),(102,'Elder Li'),(110,'Elder Lim')
				""");
		jdbc.update("""
				INSERT INTO caregiver (id,user_id,full_name,status) VALUES (201,14,'Caregiver Mei','AVAILABLE')
				""");
		jdbc.update("""
				INSERT INTO elder_family_binding (id,elder_id,family_member_id,relationship,access_scope,status) VALUES
				(701,101,42,'DAUGHTER','FULL','ACTIVE'),(702,102,42,'DAUGHTER','READ_ONLY','ACTIVE'),(710,110,7,'SON','FULL','ACTIVE')
				""");
		jdbc.update("""
				INSERT INTO visit (id,elder_id,caregiver_id,service_type,scheduled_start,scheduled_end,checked_in_at,status) VALUES
				(501,101,201,'Home care',?,?,?,'IN_PROGRESS'),
				(502,101,NULL,NULL,?,NULL,NULL,'SCHEDULED'),
				(503,102,201,'Mobility support',?,?,NULL,'SCHEDULED'),
				(510,110,201,'OTHER-FAMILY-PRIVATE',?,NULL,NULL,'SCHEDULED')
				""", at("2026-09-30T09:00:00"), at("2026-09-30T10:00:00"), at("2026-09-30T09:03:00"), at("2026-10-01T09:00:00"), at("2026-10-01T09:00:00"), at("2026-10-01T10:00:00"), at("2026-10-01T09:00:00"));
		jdbc.update("""
				INSERT INTO visit_state_transition (id,visit_id,from_state,to_state,actor_user_id,result,rejection_reason,occurred_at) VALUES
				(801,501,'SCHEDULED','ARRIVED',14,'APPLIED',NULL,?),
				(802,501,'ARRIVED','IN_PROGRESS',14,'APPLIED',NULL,?),
				(803,501,'IN_PROGRESS','VERIFIED',14,'REJECTED','INTERNAL-REJECTION',?)
				""", at("2026-09-30T09:03:00"), at("2026-09-30T09:05:00"), at("2026-09-30T09:06:00"));
		jdbc.update("""
				INSERT INTO visit_task (id,visit_id,name,status,completed_at,outcome,caregiver_note) VALUES
				(901,501,'Assisted walking','DONE',?,'PRIVATE-OUTCOME','PRIVATE-NOTE'),
				(902,501,'Meal preparation','PENDING',NULL,NULL,NULL),
				(903,501,'<strong>Optional exercise</strong> — a longer task name that must wrap on a narrow phone screen','SKIPPED',NULL,'PRIVATE-OUTCOME','PRIVATE-NOTE'),
				(904,501,'Optional reading','REFUSED',NULL,NULL,NULL)
				""", at("2026-09-30T09:15:00"));
	}

	@Test
	void sessionToScheduleToProgressReturnsPublicFactsAndAuditsWithoutCareWrites() throws Exception {
		var original = careFacts();
		try (var family = newBrowser()) {
			login(family, "family-a");
			assertThat(getJson(family, "/api/elders")).extracting(node -> node.path("id").longValue())
					.containsExactly(101L, 102L);
			var schedule = getJson(family, "/api/visits?elderId=101&dateFrom=2026-09-28&dateTo=2026-10-04");
			long visitId = schedule.path("items").get(0).path("id").longValue();
			assertThat(visitId).isEqualTo(501);
			var detail = getJson(family, "/api/visits/" + visitId);
			var timeline = getJson(family, "/api/visits/" + visitId + "/timeline");
			var tasks = getJson(family, "/api/visits/" + visitId + "/tasks");
			assertThat(detail.path("status").asString()).isEqualTo("IN_PROGRESS");
			assertThat(detail.path("checkedInAt").asString()).isEqualTo("2026-09-30T09:03:00+08:00");
			assertThat(detail.path("checkedOutAt").isNull()).isTrue();
			assertThat(detail.path("asOf").asString()).isEqualTo("2026-09-30T09:20:00+08:00");
			assertThat(timeline).extracting(node -> node.path("id").longValue()).containsExactly(801L, 802L);
			assertThat(tasks).extracting(node -> node.path("status").asString())
					.containsExactly("DONE", "PENDING", "SKIPPED", "REFUSED");
			assertThat(List.of(detail, timeline, tasks).toString()).doesNotContain("PRIVATE-", "INTERNAL-",
					"REJECTED", "carePlanNodeId", "stateDeadline", "version", "actorUserId", "rejectionReason");
			assertThat(audits()).hasSize(3).extracting(row -> row.get("detail"))
					.containsExactly("FM03_READ_VISIT", "FM03_READ_VISIT_TIMELINE", "FM03_READ_VISIT_TASKS");
			assertThat(audits()).allSatisfy(row -> {
				assertThat(row.get("actor_user_id")).isEqualTo(7L);
				assertThat(row.get("resource_type")).isEqualTo("VISIT");
				assertThat(row.get("resource_id")).isEqualTo(501L);
				assertThat(row.get("result")).isEqualTo("OK");
			});
		}
		assertThat(careFacts()).isEqualTo(original);
	}

	@Test
	void nextRoundReadsChangedFactsWithoutInventingMissingHistoryOrTaskCompletion() throws Exception {
		try (var family = newBrowser()) {
			login(family, "family-a");
			assertThat(getJson(family, "/api/visits/501").path("status").asString()).isEqualTo("IN_PROGRESS");
			assertThat(getJson(family, "/api/visits/501/tasks").get(1).path("status").asString()).isEqualTo("PENDING");
			assertThat(getJson(family, "/api/visits/501/timeline")).hasSize(2);
			// Isolated upstream fixture writes, deliberately independent of CG03/CG05.
			jdbc.update("UPDATE visit SET status = 'COMPLETED', checked_out_at = ? WHERE id = 501", at("2026-10-01T00:20:00"));
			jdbc.update("UPDATE visit_task SET status = 'DONE', completed_at = ? WHERE id = 902", at("2026-10-01T00:19:00"));
			var changed = careFacts();
			var detail = getJson(family, "/api/visits/501");
			assertThat(detail.path("status").asString()).isEqualTo("COMPLETED");
			assertThat(detail.path("checkedOutAt").asString()).isEqualTo("2026-10-01T00:20:00+08:00");
			assertThat(getJson(family, "/api/visits/501/timeline")).hasSize(2);
			assertThat(getJson(family, "/api/visits/501/tasks")).extracting(node -> node.path("status").asString())
					.containsExactly("DONE", "DONE", "SKIPPED", "REFUSED");
			assertThat(careFacts()).isEqualTo(changed);
			jdbc.update("""
					INSERT INTO visit_state_transition (id, visit_id, from_state, to_state, result, occurred_at)
					VALUES (804, 501, 'IN_PROGRESS', 'COMPLETED', 'APPLIED', ?)
					""", at("2026-10-01T00:20:00"));
			var afterHistory = careFacts();
			var latest = getJson(family, "/api/visits/501/timeline");
			assertThat(latest).hasSize(3);
			assertThat(latest.get(2).path("occurredAt").asString()).isEqualTo("2026-10-01T00:20:00+08:00");
			assertThat(careFacts()).isEqualTo(afterHistory);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"REVOKED", "EXPIRED"})
	void bindingLossBetweenSectionsDeniesSubsequentReadsInSameSession(String loss) throws Exception {
		try (var family = newBrowser()) {
			login(family, "family-a");
			String session = cookie(family, "JSESSIONID").getValue();
			jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE id = 701", at("2026-09-30T09:20:01"));
			assertThat(getJson(family, "/api/visits/501").path("id").longValue()).isEqualTo(501);
			if (loss.equals("REVOKED")) {
				jdbc.update("UPDATE elder_family_binding SET status = 'REVOKED' WHERE id = 701");
			} else {
				jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE id = 701", at("2026-09-30T09:20:00"));
			}
			for (String suffix : List.of("/timeline", "/tasks", "")) {
				assertReadDenied(family, "/api/visits/501" + suffix, 403);
			}
			assertThat(getJson(family, "/api/auth/me").path("id").longValue()).isEqualTo(7);
			assertThat(cookie(family, "JSESSIONID").getValue()).isEqualTo(session);
			assertThat(audits()).filteredOn(row -> row.get("result").equals("DENIED")).hasSize(3);
		}
	}

	@Test
	void readOnlyEmptyVisitWorksButForgedIdentityCannotExposeAnotherFamily() throws Exception {
		try (var family = newBrowser(); var other = newBrowser()) {
			login(family, "family-a");
			login(other, "family-b");
			assertThat(getJson(family, "/api/visits/503").path("status").asString()).isEqualTo("SCHEDULED");
			assertThat(getJson(family, "/api/visits/503/timeline")).isEmpty();
			assertThat(getJson(family, "/api/visits/503/tasks")).isEmpty();
			for (String suffix : List.of("", "/timeline", "/tasks")) {
				assertReadDenied(family, "/api/visits/510" + suffix + "?elderId=101&familyMemberId=7&userId=9", 403);
				assertReadDenied(other, "/api/visits/501" + suffix + "?elderId=110&familyMemberId=42&userId=7", 403);
			}
			assertThat(getJson(other, "/api/visits/510").path("elderId").longValue()).isEqualTo(110);
		}
	}

	@Test
	void logoutInvalidatesEverySectionAndReplayedCookieThenLoginReauthorizes() throws Exception {
		try (var family = newBrowser(); var replay = newBrowser()) {
			login(family, "family-a");
			getJson(family, "/api/visits/501");
			String oldSession = cookie(family, "JSESSIONID").getValue();
			get(family, "/api/auth/csrf");
			var logout = HttpRequest.newBuilder(uri("/api/auth/logout")).timeout(Duration.ofSeconds(10))
					.header("X-XSRF-TOKEN", cookie(family, "XSRF-TOKEN").getValue())
					.POST(HttpRequest.BodyPublishers.noBody()).build();
			assertThat(family.client().send(logout, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(204);
			var oldCookie = new HttpCookie("JSESSIONID", oldSession);
			oldCookie.setPath("/");
			oldCookie.setVersion(0);
			replay.cookies().getCookieStore().add(uri("/"), oldCookie);
			for (var browser : List.of(family, replay)) {
				for (String suffix : List.of("", "/timeline", "/tasks")) {
					assertReadDenied(browser, "/api/visits/501" + suffix, 401);
				}
			}
			assertThat(audits()).hasSize(1);
			login(family, "family-a");
			assertThat(cookie(family, "JSESSIONID").getValue()).isNotEqualTo(oldSession);
			for (String suffix : List.of("", "/timeline", "/tasks")) {
				getJson(family, "/api/visits/501" + suffix);
			}
			assertThat(audits()).hasSize(4);
		}
	}

	@Test
	void auditOutageHidesAllSectionsAndRecoveryRestoresReadsWithoutCareWrites() throws Exception {
		var original = careFacts();
		try (var family = newBrowser()) {
			login(family, "family-a");
			jdbc.execute("RENAME TABLE audit_log TO fm03_workflow_audit_unavailable");
			try {
				for (String suffix : List.of("", "/timeline", "/tasks")) {
					assertReadDenied(family, "/api/visits/501" + suffix, 503);
				}
			} finally {
				jdbc.execute("RENAME TABLE fm03_workflow_audit_unavailable TO audit_log");
			}
			for (String suffix : List.of("", "/timeline", "/tasks")) {
				getJson(family, "/api/visits/501" + suffix);
			}
			assertThat(audits()).hasSize(3);
		}
		assertThat(careFacts()).isEqualTo(original);
	}

	@Test
	void progressPreservesManagerDetailCaregiverWorkPackAndOtherFamilyReads() throws Exception {
		var original = careFacts();
		try (var family = newBrowser(); var manager = newBrowser(); var caregiver = newBrowser()) {
			login(family, "family-a");
			login(manager, "manager");
			login(caregiver, "caregiver");
			for (String suffix : List.of("", "/timeline", "/tasks")) {
				getJson(family, "/api/visits/501" + suffix);
			}
			var managerDetail = getJson(manager, "/api/visits/501");
			assertThat(managerDetail.has("version")).isTrue();
			assertThat(managerDetail.has("asOf")).isFalse();
			assertThat(getJson(caregiver, "/api/visits/501/work-pack").toString()).contains("Meal preparation");
			for (var browser : List.of(manager, caregiver)) {
				assertReadDenied(browser, "/api/visits/501/timeline", 403);
			}
			assertReadDenied(manager, "/api/visits/501/tasks", 403);
			assertThat(getJson(caregiver, "/api/visits/501/tasks").toString()).contains("Meal preparation", "PRIVATE-NOTE");
			assertThat(getJson(family, "/api/intake-applications").path("items")).isEmpty();
			assertThat(getJson(family, "/api/visits?elderId=101").path("totalElements").longValue()).isEqualTo(2);
		}
		assertThat(careFacts()).isEqualTo(original);
	}

	private void assertReadDenied(Browser browser, String path, int status) throws Exception {
		var response = get(browser, path);
		assertThat(response.statusCode()).as("GET %s: %s", path, response.body()).isEqualTo(status);
		assertThat(response.body()).doesNotContain("Home care", "PRIVATE-", "INTERNAL-", "Meal preparation",
				"Assisted walking", "OTHER-FAMILY", "checkedInAt", "occurredAt", "completedAt");
	}

	private static Timestamp at(String value) {
		return Timestamp.valueOf(LocalDateTime.parse(value));
	}

	private List<List<Map<String, Object>>> careFacts() {
		return List.of("visit", "visit_task", "visit_state_transition").stream()
				.map(table -> jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id")).toList();
	}

	private List<Map<String, Object>> audits() {
		return jdbc.queryForList("SELECT actor_user_id, resource_type, resource_id, result, detail FROM audit_log "
				+ "WHERE action = 'READ' AND detail LIKE 'FM03_%' ORDER BY id");
	}

	private Browser newBrowser() {
		var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		var client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
		return new Browser(client, cookies);
	}

	private void login(Browser browser, String username) throws Exception {
		var bootstrap = get(browser, "/api/auth/csrf");
		assertThat(bootstrap.statusCode()).isEqualTo(200);
		assertThat(bootstrap.body()).isEmpty();
		assertThat(cookie(browser, "XSRF-TOKEN").isHttpOnly()).isFalse();
		var request = HttpRequest.newBuilder(uri("/api/auth/login")).timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.header("X-XSRF-TOKEN", cookie(browser, "XSRF-TOKEN").getValue())
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
						Map.of("username", username, "password", "test-password")))).build();
		var response = browser.client().send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).as("Login response: %s", response.body()).isEqualTo(200);
		assertThat(json.readTree(response.body()).path("username").asString()).isEqualTo(username);
		assertThat(cookie(browser, "JSESSIONID").getValue()).isNotBlank();
		assertThat(cookie(browser, "JSESSIONID").isHttpOnly()).isTrue();
	}

	private HttpCookie cookie(Browser browser, String name) {
		return browser.cookies().getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals(name))
				.findFirst().orElseThrow(() -> new AssertionError("Missing response cookie: " + name));
	}

	private JsonNode getJson(Browser browser, String path) throws Exception {
		var response = get(browser, path);
		assertThat(response.statusCode()).as("GET %s: %s", path, response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type ->
				assertThat(type).startsWith("application/json"));
		return json.readTree(response.body());
	}

	private HttpResponse<String> get(Browser browser, String path) throws Exception {
		var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build();
		return browser.client().send(request, HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://127.0.0.1:" + port + path);
	}

	private record Browser(HttpClient client, CookieManager cookies) implements AutoCloseable {
		@Override
		public void close() {
			client.close();
		}
	}

}
