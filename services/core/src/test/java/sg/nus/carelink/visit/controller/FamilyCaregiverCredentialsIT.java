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
 * Verifies public caregiver credentials and current family access against MySQL.
 *
 * @author Wang Zhili
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FamilyCaregiverCredentialsIT.FixedTime.class)
class FamilyCaregiverCredentialsIT {

	private static final String PASSWORD = "test-password";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 0, 30);

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyCaregiverCredentialsIT.class, "+05:00");
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
		Clock familyCaregiverCredentialsClock() {
			return Clock.fixed(Instant.parse("2026-09-27T16:30:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void prepareIsolatedCredentials() {
		jdbc.update("UPDATE credential SET renews_credential_id = NULL");
		jdbc.update("DELETE FROM credential");
		jdbc.update("DELETE FROM credential_type");
		jdbc.update("DELETE FROM visit_assignment");
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
				(10, 'manager', '{noop}test-password', 'Manager'),
				(12, 'no-profile', '{noop}test-password', 'Missing profile'),
				(13, 'no-binding', '{noop}test-password', 'Family without bindings'),
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
				(42, 7, 'Family A'), (7, 9, 'Family B'), (55, 13, 'Family without bindings')
				""");
		for (long id : List.of(101L, 102L, 110L)) {
			jdbc.update("INSERT INTO elder (id, full_name) VALUES (?, ?)", id, "Test elder " + id);
		}
		for (long id : List.of(201L, 202L, 203L, 204L, 205L, 206L)) {
			jdbc.update("""
					INSERT INTO caregiver (id, user_id, full_name, status)
					VALUES (?, ?, ?, 'AVAILABLE')
					""", id, id + 1000, "Test caregiver " + id);
		}
		seedBinding(101, 42, "FULL", null);
		seedBinding(102, 42, "READ_ONLY", NOW.plusSeconds(1));
		seedBinding(110, 7, "FULL", null);
		seedVisit(301, 101, 201, "2026-09-28T10:00:00", "SCHEDULED");
		seedVisit(302, 102, 202, "2020-01-01T10:00:00", "CANCELLED");
		seedVisit(303, 101, 203, "2026-09-28T12:00:00", "SCHEDULED");
		seedVisit(304, 110, 204, "2026-09-28T10:00:00", "SCHEDULED");
		jdbc.update("UPDATE caregiver SET status = 'INACTIVE' WHERE id = 202");
		jdbc.update("""
				INSERT INTO visit_assignment (visit_id, caregiver_id, status, assigned_at, ended_at)
				VALUES (301, 205, 'REPLACED', '2026-09-25 10:00:00', '2026-09-26 10:00:00')
				""");
		jdbc.update("""
				INSERT INTO credential_type (id, name) VALUES
				(701, 'First Aid'), (702, 'Mobility Support'), (703, 'Vitals')
				""");
		seedCredential(901, 201, 702, "PUBLISHED", "2026-01-01", "2027-01-01");
		seedCredential(902, 201, 701, "EXPIRING", "2026-01-01", "2026-09-27");
		seedCredential(903, 201, 701, "PUBLISHED", "2026-01-01", "2026-09-27");
		seedCredential(904, 201, 701, "PUBLISHED", "2026-01-01", "2026-09-28");
		seedCredential(905, 201, 702, "EXPIRING", "2026-01-01", "2026-09-28");
		seedCredential(906, 201, 702, "REVOKED", "2026-01-01", "2026-09-27");
		seedCredential(907, 201, 702, "EXPIRED", "2026-01-01", "2027-01-01");
		seedCredential(908, 201, 703, "PUBLISHED", "2026-10-01", "2027-10-01");
		seedCredential(909, 201, 703, "PUBLISHED", null, "9999-12-31");
		seedCredential(910, 201, 703, "PUBLISHED", "2026-01-01", "2026-10-05");
		seedCredential(911, 201, 701, "SUBMITTED", "2026-01-01", "2027-01-01");
		seedCredential(912, 201, 702, "REJECTED", "2026-01-01", "2027-01-01");
		seedCredential(920, 202, 701, "EXPIRING", "2026-01-01", "2026-10-01");
		seedCredential(930, 203, 701, "SUBMITTED", "2026-01-01", "2027-01-01");
		seedCredential(931, 203, 702, "REJECTED", "2026-01-01", "2027-01-01");
		seedCredential(940, 204, 701, "PUBLISHED", "2026-01-01", "2027-01-01");
		seedCredential(950, 205, 701, "PUBLISHED", "2026-01-01", "2027-01-01");
		jdbc.update("UPDATE credential SET issuing_body = NULL WHERE id = 909");
		jdbc.update("UPDATE credential SET renews_credential_id = 903 WHERE id = 901");
	}

	@Test
	void familySessionReadsOnlyPublicCredentialFieldsInTypeAndIdentifierOrderWithoutCsrf() throws Exception {
		var body = readCredentials(loginAs("family-a"), 201);
		assertThat(body).extracting(item -> item.path("id").longValue())
				.containsExactly(902L, 903L, 904L, 901L, 905L, 906L, 907L, 908L, 909L, 910L);
		assertThat(body).isNotEmpty().allSatisfy(item -> {
			assertThat(item.propertyNames()).containsExactlyInAnyOrder("id", "caregiverId", "credentialTypeId",
					"credentialTypeName", "issuingBody", "validFrom", "expiryDate", "status");
			assertThat(item.path("caregiverId").longValue()).isEqualTo(201);
		});
		assertThat(body.get(0).path("credentialTypeId").longValue()).isEqualTo(701);
		assertThat(body.get(0).path("credentialTypeName").asString()).isEqualTo("First Aid");
		assertThat(body.get(3).path("credentialTypeId").longValue()).isEqualTo(702);
		assertThat(body.get(3).path("credentialTypeName").asString()).isEqualTo("Mobility Support");
		assertThat(body.get(7).path("credentialTypeId").longValue()).isEqualTo(703);
		assertThat(body.get(7).path("credentialTypeName").asString()).isEqualTo("Vitals");
		assertThat(body.get(0).path("issuingBody").asString()).isEqualTo("Training provider 902");
		assertThat(body.get(0).path("validFrom").asString()).isEqualTo("2026-01-01");
	}

	@Test
	void publishedAndExpiringCredentialsExpireOnSingaporeCalendarAfterExpiryDay() throws Exception {
		var body = readCredentials(loginAs("family-a"), 201);
		assertThat(body.get(0).path("status").asString()).isEqualTo("EXPIRED");
		assertThat(body.get(1).path("status").asString()).isEqualTo("EXPIRED");
		assertThat(body.get(0).path("expiryDate").asString()).isEqualTo("2026-09-27");
		assertThat(body.get(1).path("expiryDate").asString()).isEqualTo("2026-09-27");
		assertThat(body.get(2).path("status").asString()).isEqualTo("PUBLISHED");
		assertThat(body.get(4).path("status").asString()).isEqualTo("EXPIRING");
	}

	@Test
	void revokedAndExpiredStatusesArePreservedWithoutRevivingOrAutoWarning() throws Exception {
		var body = readCredentials(loginAs("family-a"), 201);
		assertThat(body.get(5).path("status").asString()).isEqualTo("REVOKED");
		assertThat(body.get(6).path("status").asString()).isEqualTo("EXPIRED");
		assertThat(body.get(6).path("expiryDate").asString()).isEqualTo("2027-01-01");
		assertThat(body.get(9).path("status").asString()).isEqualTo("PUBLISHED");
		assertThat(body.get(9).path("expiryDate").asString()).isEqualTo("2026-10-05");
	}

	@Test
	void futureValidityAndPermanentCredentialsRetainDatesAndNullableFields() throws Exception {
		var body = readCredentials(loginAs("family-a"), 201);
		assertThat(body.get(7).path("status").asString()).isEqualTo("PUBLISHED");
		assertThat(body.get(7).path("validFrom").asString()).isEqualTo("2026-10-01");
		assertThat(body.get(8).path("status").asString()).isEqualTo("PUBLISHED");
		assertThat(body.get(8).path("expiryDate").asString()).isEqualTo("9999-12-31");
		assertThat(body.get(8).has("issuingBody")).isTrue();
		assertThat(body.get(8).get("issuingBody").isNull()).isTrue();
		assertThat(body.get(8).has("validFrom")).isTrue();
		assertThat(body.get(8).get("validFrom").isNull()).isTrue();
	}

	@Test
	void readingDerivedStatusesDoesNotWriteCredentialStatusOrUpdatedTime() throws Exception {
		var before = jdbc.queryForList("SELECT id, status, updated_at FROM credential ORDER BY id");
		readCredentials(loginAs("family-a"), 201);
		var after = jdbc.queryForList("SELECT id, status, updated_at FROM credential ORDER BY id");
		assertThat(after).isEqualTo(before);
	}

	@Test
	void authorizedCaregiverWithOnlyPrivateCredentialsReturnsEmptyArray() throws Exception {
		var session = loginAs("family-a");
		assertThat(readCredentials(session, 203)).isEmpty();
		jdbc.update("DELETE FROM credential WHERE caregiver_id = 203");
		assertThat(readCredentials(session, 203)).isEmpty();
	}

	@Test
	void historicalCancelledVisitAndReadOnlyBindingPermitInactiveCaregiverUntilExpiry() throws Exception {
		var session = loginAs("family-a");
		var body = readCredentials(session, 202);
		assertThat(body).hasSize(1);
		assertThat(body.get(0).path("id").longValue()).isEqualTo(920);
		assertThat(body.get(0).path("status").asString()).isEqualTo("EXPIRING");
		jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 102", NOW);
		mvc.perform(get(path("202")).session(session)).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(longs = { 204, 205, 206, 999, 0, -1 })
	void unrelatedMissingAndReplacedOnlyCaregiversCannotDiscloseCredentials(long caregiverId) throws Exception {
		mvc.perform(get(path(Long.toString(caregiverId))).session(loginAs("family-a")))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.credentialTypeName").doesNotExist());
	}

	@Test
	void anotherFamilyCanReadOnlyItsOwnCaregiverRegardlessOfIdentityParameters() throws Exception {
		var session = loginAs("family-b");
		var body = readCredentials(session, 204);
		assertThat(body).hasSize(1);
		assertThat(body.get(0).path("id").longValue()).isEqualTo(940);
		mvc.perform(get(path("201")).session(session)
				.param("familyMemberId", "42").param("userId", "7").param("role", "FAMILY"))
				.andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = { "REVOKED", "PENDING_CONFIRMATION", "REJECTED" })
	void bindingChangesRemoveAccessOnTheNextRead(String bindingStatus) throws Exception {
		var session = loginAs("family-a");
		assertThat(readCredentials(session, 201)).isNotEmpty();
		jdbc.update("UPDATE elder_family_binding SET status = ? WHERE elder_id = 101", bindingStatus);
		mvc.perform(get(path("201")).session(session)).andExpect(status().isForbidden());
	}

	@Test
	void profileReadDoesNotGrantLaterCredentialsAccessAfterVisitRelationshipChanges() throws Exception {
		var session = loginAs("family-a");
		mvc.perform(get("/api/caregivers/201").session(session)).andExpect(status().isOk());
		jdbc.update("UPDATE visit SET caregiver_id = NULL WHERE id = 301");
		mvc.perform(get(path("201")).session(session)).andExpect(status().isForbidden());
	}

	@Test
	void anotherValidBindingRetainsAccessAfterOneBindingIsRevoked() throws Exception {
		var session = loginAs("family-a");
		seedVisit(305, 102, 201, "2026-09-29T10:00:00", "SCHEDULED");
		jdbc.update("UPDATE elder_family_binding SET status = 'REVOKED' WHERE elder_id = 101");
		assertThat(readCredentials(session, 201)).isNotEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "no-profile", "no-binding", "manager", "caregiver", "elder" })
	void accountsWithoutFamilyProfileAccessAreForbidden(String username) throws Exception {
		mvc.perform(get(path("201")).session(loginAs(username))).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = { "disabled", "role-removed", "profile-detached" })
	void currentAccountAndProfileAccessIsRecheckedAfterLogin(String change) throws Exception {
		var session = loginAs("family-a");
		assertThat(readCredentials(session, 201)).isNotEmpty();
		switch (change) {
			case "disabled" -> jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = 7");
			case "role-removed" -> jdbc.update("DELETE FROM user_role WHERE user_id = 7 AND role = 'FAMILY'");
			case "profile-detached" -> jdbc.update("UPDATE family_member SET user_id = NULL WHERE id = 42");
			default -> throw new IllegalArgumentException("Unknown account change: " + change);
		}
		mvc.perform(get(path("201")).session(session)).andExpect(status().isForbidden());
	}

	@Test
	void anonymousRequestsRequireLogin() throws Exception {
		mvc.perform(get(path("201"))).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "not-a-number", "9223372036854775808" })
	void malformedPathIdentifiersReturnBadRequest(String caregiverId) throws Exception {
		mvc.perform(get(path(caregiverId)).session(loginAs("family-a")))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
	}

	private JsonNode readCredentials(MockHttpSession session, long caregiverId) throws Exception {
		var response = mvc.perform(get(path(Long.toString(caregiverId))).session(session))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andReturn().getResponse();
		var body = json.readTree(response.getContentAsString());
		assertThat(body.isArray()).isTrue();
		return body;
	}

	private static String path(String caregiverId) {
		return "/api/caregivers/" + caregiverId + "/credentials";
	}

	private void seedCredential(long id, long caregiverId, long typeId, String credentialStatus,
			String validFrom, String expiryDate) {
		jdbc.update("""
				INSERT INTO credential
				(id, caregiver_id, credential_type_id, reviewed_by_user_id, certificate_no,
				 issuing_body, valid_from, expiry_date, status, updated_at)
				VALUES (?, ?, ?, 10, ?, ?, ?, ?, ?, '2026-09-01 10:00:00')
				""", id, caregiverId, typeId, "PRIVATE-CERT-" + id, "Training provider " + id,
				validFrom, expiryDate, credentialStatus);
	}

	private void seedVisit(long id, long elderId, long caregiverId, String start, String visitStatus) {
		jdbc.update("""
				INSERT INTO visit (id, elder_id, caregiver_id, service_type, scheduled_start, status)
				VALUES (?, ?, ?, 'BATHING', ?, ?)
				""", id, elderId, caregiverId, LocalDateTime.parse(start), visitStatus);
	}

	private void seedBinding(long elderId, long familyId, String scope, LocalDateTime expiresAt) {
		jdbc.update("""
				INSERT INTO elder_family_binding
				(elder_id, family_member_id, relationship, access_scope, status, confirmed_at, expires_at)
				VALUES (?, ?, 'DAUGHTER', ?, 'ACTIVE', ?, ?)
				""", elderId, familyId, scope, NOW.minusDays(1), expiresAt);
	}

	private MockHttpSession loginAs(String username) throws Exception {
		Cookie token = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		var result = mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
				.andExpect(status().isOk()).andReturn();
		var session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session).isNotNull();
		return session;
	}
}
