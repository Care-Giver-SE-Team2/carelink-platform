package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * Verifies the family schedule workflow over HTTP with server sessions and isolated MySQL data.
 *
 * @author Wang Zhili
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FamilyScheduleWorkflowIT.FixedTime.class)
class FamilyScheduleWorkflowIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyScheduleWorkflowIT.class, "+05:00");
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
		Clock familyScheduleWorkflowClock() {
			return Clock.fixed(Instant.parse("2026-09-27T16:30:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void prepareIsolatedWeekSchedule() {
		jdbc.update("DELETE FROM audit_log");
		jdbc.update("DELETE FROM intake_application");
		jdbc.update("DELETE FROM credential");
		jdbc.update("DELETE FROM credential_type");
		jdbc.update("DELETE FROM visit");
		jdbc.update("DELETE FROM elder_family_binding");
		jdbc.update("DELETE FROM elder");
		jdbc.update("DELETE FROM family_member");
		jdbc.update("DELETE FROM caregiver");
		jdbc.update("DELETE FROM user_role");
		jdbc.update("DELETE FROM app_user");
		jdbc.update("""
				INSERT INTO app_user (id, username, password_hash, display_name) VALUES
				(7, 'family-a', '{noop}test-password', 'Family A'),
				(9, 'family-b', '{noop}test-password', 'Family B'),
				(10, 'manager', '{noop}test-password', 'Manager')
				""");
		jdbc.update("""
				INSERT INTO user_role (user_id, role) VALUES (7, 'FAMILY'), (9, 'FAMILY'), (10, 'MANAGER')
				""");
		jdbc.update("""
				INSERT INTO family_member (id, user_id, full_name) VALUES (42, 7, 'Family A'), (7, 9, 'Family B')
				""");
		jdbc.update("""
				INSERT INTO elder (id, full_name, medical_notes) VALUES
				(101, 'Elder Tan', 'PRIVATE-MEDICAL'), (110, 'Other elder', 'OTHER-MEDICAL')
				""");
		jdbc.update("""
				INSERT INTO caregiver (id, user_id, full_name, phone, dialects, status) VALUES
				(201, 1201, 'Caregiver Mei', 'PRIVATE-PHONE', 'Mandarin, Hokkien', 'AVAILABLE'),
				(202, 1202, 'Caregiver Lee', 'OTHER-PHONE', 'English', 'AVAILABLE'),
				(204, 1204, 'Other caregiver', 'OTHER-PHONE', 'English', 'AVAILABLE')
				""");
		jdbc.update("""
				INSERT INTO elder_family_binding
				(id, elder_id, family_member_id, relationship, access_scope, status, confirmed_at)
				VALUES (501, 101, 42, 'DAUGHTER', 'FULL', 'ACTIVE', '2026-09-01 10:00:00'),
				(502, 110, 7, 'SON', 'READ_ONLY', 'ACTIVE', '2026-09-01 10:00:00')
				""");
		jdbc.update("""
				INSERT INTO visit (id, elder_id, caregiver_id, service_type, scheduled_start, status) VALUES
				(301, 101, 201, 'BATHING', '2026-09-28 10:00:00', 'SCHEDULED'),
				(302, 101, 202, 'VITALS', '2026-09-30 11:00:00', 'SCHEDULED'),
				(303, 101, 201, 'BATHING', '2026-10-04 23:30:00', 'SCHEDULED'),
				(304, 101, 201, 'BATHING', '2026-10-05 00:00:00', 'SCHEDULED'),
				(305, 101, 201, 'BATHING', '2026-09-27 23:59:59', 'SCHEDULED'),
				(390, 110, 204, 'BATHING', '2026-09-28 09:00:00', 'SCHEDULED')
				""");
		jdbc.update("INSERT INTO credential_type (id, name) VALUES (701, 'First Aid')");
		jdbc.update("""
				INSERT INTO credential (id, caregiver_id, credential_type_id, certificate_no,
				issuing_body, valid_from, expiry_date, status) VALUES
				(901, 201, 701, 'PRIVATE-VALID-CERT', 'Training provider', '2026-01-01', '2026-09-28', 'PUBLISHED'),
				(902, 201, 701, 'PRIVATE-EXPIRED-CERT', 'Training provider', '2026-01-01', '2026-09-27', 'PUBLISHED'),
				(903, 201, 701, 'PRIVATE-PENDING-CERT', 'Training provider', '2026-01-01', '2027-09-27', 'SUBMITTED')
				""");
	}

	@Test
	void sessionCookiesConnectElderSelectionWeekPaginationCaregiverAndPublicQualifications() throws Exception {
		try (var browser = newBrowser()) {
			login(browser, "family-a");
			var currentUser = getJson(browser, "/api/auth/me");
			assertThat(currentUser.path("id").longValue()).isEqualTo(7L);
			assertThat(currentUser.path("roles")).extracting(JsonNode::asString).containsExactly("FAMILY");

			var elders = getJson(browser, "/api/elders");
			assertThat(elders.isArray()).isTrue();
			assertThat(elders).extracting(elder -> elder.path("id").longValue()).containsExactly(101L);
			long selectedElderId = elders.get(0).path("id").longValue();
			var firstPage = getJson(browser, "/api/visits?elderId=" + selectedElderId + "&page=0&size=2");
			assertThat(firstPage.path("page").intValue()).isZero();
			assertThat(firstPage.path("size").intValue()).isEqualTo(2);
			assertThat(firstPage.path("totalElements").longValue()).isEqualTo(3L);
			assertThat(firstPage.path("items")).extracting(visit -> visit.path("id").longValue())
					.containsExactly(301L, 302L);
			assertThat(firstPage.path("items").get(0).path("scheduledStart").asString())
					.isEqualTo("2026-09-28T10:00:00+08:00");
			assertThat(firstPage.path("items").get(0).path("asOf").asString())
					.isEqualTo("2026-09-28T00:30:00+08:00");
			var secondPage = getJson(browser, "/api/visits?elderId=" + selectedElderId + "&page=1&size=2");
			assertThat(secondPage.path("page").intValue()).isEqualTo(1);
			assertThat(secondPage.path("totalElements").longValue()).isEqualTo(3L);
			assertThat(secondPage.path("items")).extracting(visit -> visit.path("id").longValue())
					.containsExactly(303L);

			long selectedCaregiverId = firstPage.path("items").get(0).path("caregiverId").longValue();
			var caregiver = getJson(browser, "/api/caregivers/" + selectedCaregiverId);
			assertThat(caregiver.path("id").longValue()).isEqualTo(201L);
			assertThat(caregiver.path("fullName").asString()).isEqualTo("Caregiver Mei");
			assertThat(caregiver.propertyNames()).containsExactlyInAnyOrder("id", "fullName", "dialects");
			assertThat(caregiver.path("dialects")).extracting(JsonNode::asString).containsExactly("Mandarin", "Hokkien");
			var credentials = getJson(browser, "/api/caregivers/" + selectedCaregiverId + "/credentials");
			assertThat(credentials).extracting(credential -> credential.path("id").longValue())
					.containsExactly(901L, 902L);
			assertThat(credentials.get(0).path("status").asString()).isEqualTo("PUBLISHED");
			assertThat(credentials.get(1).path("status").asString()).isEqualTo("EXPIRED");
			assertThat(credentials.toString()).doesNotContain("PRIVATE-", "certificateNo", "reviewedByUserId");

			var audits = auditRows();
			assertThat(audits).extracting(AuditEvidence::actorUserId).containsOnly(7L);
			assertThat(audits).extracting(AuditEvidence::resourceType)
					.containsExactly("ELDER", "VISIT", "VISIT", "CAREGIVER", "CAREGIVER_CREDENTIALS");
			assertThat(audits).extracting(AuditEvidence::resourceId).containsExactly(null, null, null, 201L, 201L);
			assertThat(audits).extracting(AuditEvidence::result).containsOnly("OK");
			assertThat(audits.get(1).detail()).contains("dateFrom=2026-09-28", "dateTo=2026-10-04", "page=0", "size=2");
			assertThat(audits.get(2).detail()).contains("page=1");
		}
	}

	@Test
	void revokingTheSelectedBindingRemovesAccessAcrossTheSameLoggedInWorkflow() throws Exception {
		try (var browser = newBrowser()) {
			login(browser, "family-a");
			getJson(browser, "/api/elders");
			getJson(browser, "/api/visits?elderId=101");
			getJson(browser, "/api/caregivers/201");
			getJson(browser, "/api/caregivers/201/credentials");
			var before = auditRows();
			assertThat(before).hasSize(4);

			jdbc.update("UPDATE elder_family_binding SET status = 'REVOKED' WHERE id = 501");
			assertThat(getJson(browser, "/api/auth/me").path("id").longValue()).isEqualTo(7L);
			assertThat(getJson(browser, "/api/elders")).isEmpty();
			var remainingVisits = getJson(browser, "/api/visits");
			assertThat(remainingVisits.path("items")).isEmpty();
			assertThat(remainingVisits.path("totalElements").longValue()).isZero();
			for (String path : List.of("/api/visits?elderId=101", "/api/caregivers/201", "/api/caregivers/201/credentials")) {
				var response = get(browser, path);
				assertThat(response.statusCode()).as("Revoked access to %s", path).isEqualTo(403);
				assertThat(json.readTree(response.body()).path("status").intValue()).isEqualTo(403);
				assertThat(response.body()).doesNotContain("Elder Tan", "Caregiver Mei", "Training provider", "PRIVATE-");
			}
			var after = auditRows();
			assertThat(after).hasSize(9);
			assertThat(after.subList(0, 4)).isEqualTo(before);
			assertThat(after.subList(4, 6)).extracting(AuditEvidence::result).containsExactly("OK", "OK");
			assertThat(after.subList(6, 9)).extracting(AuditEvidence::result).containsOnly("DENIED");
			assertThat(after.subList(6, 9)).extracting(AuditEvidence::resourceType)
					.containsExactly("VISIT", "CAREGIVER", "CAREGIVER_CREDENTIALS");
			assertThat(after).extracting(AuditEvidence::actorUserId).containsOnly(7L);
		}
	}

	@Test
	void logoutInvalidatesTheServerSessionAndOldCookiesCannotReopenProtectedScheduleData() throws Exception {
		try (var browser = newBrowser(); var fresh = newBrowser(); var replay = newBrowser()) {
			login(browser, "family-a");
			getJson(browser, "/api/caregivers/201");
			String oldSessionId = cookie(browser, "JSESSIONID").getValue();
			var logoutRequest = HttpRequest.newBuilder(uri("/api/auth/logout")).timeout(Duration.ofSeconds(10))
					.header("X-XSRF-TOKEN", cookie(browser, "XSRF-TOKEN").getValue())
					.POST(HttpRequest.BodyPublishers.noBody()).build();
			var logout = browser.client().send(logoutRequest, HttpResponse.BodyHandlers.ofString());
			assertThat(logout.statusCode()).isEqualTo(204);
			assertThat(logout.body()).isEmpty();
			assertThat(browser.cookies().getCookieStore().getCookies())
					.noneMatch(cookie -> cookie.getName().equals("JSESSIONID"));

			var oldCookie = new HttpCookie("JSESSIONID", oldSessionId);
			oldCookie.setPath("/");
			oldCookie.setVersion(0);
			replay.cookies().getCookieStore().add(uri("/"), oldCookie);
			for (var client : List.of(browser, fresh, replay)) {
				for (String path : List.of("/api/elders", "/api/visits?elderId=101",
						"/api/caregivers/201", "/api/caregivers/201/credentials")) {
					var response = get(client, path);
					assertThat(response.statusCode()).as("Unauthenticated read of %s", path).isEqualTo(401);
					assertThat(response.body()).doesNotContain("Elder Tan", "Caregiver Mei", "Training provider", "PRIVATE-");
				}
			}
			assertThat(auditRows()).hasSize(1);
			login(browser, "family-a");
			assertThat(cookie(browser, "JSESSIONID").getValue()).isNotEqualTo(oldSessionId);
			assertThat(getJson(browser, "/api/caregivers/201").path("id").longValue()).isEqualTo(201L);
			assertThat(auditRows()).hasSize(2).extracting(AuditEvidence::result).containsOnly("OK");
		}
	}

	@Test
	void familyScheduleReadsPreserveManagerElderListingAndFm01ApplicationOwnership() throws Exception {
		try (var family = newBrowser(); var manager = newBrowser(); var otherFamily = newBrowser()) {
			login(manager, "manager");
			var managerElders = getJson(manager, "/api/elders");
			assertThat(managerElders.isArray()).isTrue();
			assertThat(managerElders).extracting(elder -> elder.path("id").longValue())
					.containsExactlyInAnyOrder(101L, 110L);
			assertThat(auditRows()).isEmpty();

			login(family, "family-a");
			var submission = HttpRequest.newBuilder(uri("/api/intake-applications")).timeout(Duration.ofSeconds(10))
					.header("Content-Type", "application/json")
					.header("X-XSRF-TOKEN", cookie(family, "XSRF-TOKEN").getValue())
					.POST(HttpRequest.BodyPublishers.ofString("""
							{"targetElderName":"New elder","targetAddress":"12 Example Road","postalCode":"123456"}
							""")).build();
			var submitted = family.client().send(submission, HttpResponse.BodyHandlers.ofString());
			assertThat(submitted.statusCode()).as("FM01 submission: %s", submitted.body()).isEqualTo(201);
			var application = json.readTree(submitted.body());
			long applicationId = application.path("id").longValue();
			assertThat(application.path("applicantFamilyMemberId").longValue()).isEqualTo(42L);
			assertThat(application.path("status").asString()).isEqualTo("SUBMITTED");
			assertThat(application.path("elderId").isNull()).isTrue();
			assertThat(getJson(family, "/api/visits?elderId=101").path("totalElements").longValue()).isEqualTo(3L);
			var applications = getJson(family, "/api/intake-applications");
			assertThat(applications.path("totalElements").longValue()).isEqualTo(1L);
			assertThat(applications.path("items").get(0)).isEqualTo(application);
			assertThat(getJson(family, "/api/intake-applications/" + applicationId)).isEqualTo(application);

			login(otherFamily, "family-b");
			assertThat(getJson(otherFamily, "/api/intake-applications").path("items")).isEmpty();
			var unauthorizedDetail = get(otherFamily, "/api/intake-applications/" + applicationId);
			assertThat(unauthorizedDetail.statusCode()).isEqualTo(403);
			assertThat(unauthorizedDetail.body()).doesNotContain("New elder", "12 Example Road");
			assertThat(auditRows()).hasSize(1);
			assertThat(auditRows().getFirst().actorUserId()).isEqualTo(7L);
			assertThat(auditRows().getFirst().resourceType()).isEqualTo("VISIT");
			assertThat(auditRows().getFirst().result()).isEqualTo("OK");
		}
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

	private List<AuditEvidence> auditRows() {
		return jdbc.query("SELECT actor_user_id, resource_type, resource_id, result, detail FROM audit_log "
				+ "WHERE action = 'READ' ORDER BY id", (row, index) -> new AuditEvidence(row.getLong("actor_user_id"),
				row.getString("resource_type"), (Long) row.getObject("resource_id"), row.getString("result"), row.getString("detail")));
	}

	private record Browser(HttpClient client, CookieManager cookies) implements AutoCloseable {
		@Override
		public void close() {
			client.close();
		}
	}

	private record AuditEvidence(long actorUserId, String resourceType, Long resourceId, String result, String detail) {
	}
}
