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
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
 * Verifies public caregiver profiles against current family access and MySQL visit data.
 *
 * @author Wang Zhili
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FamilyCaregiverProfileIT.FixedTime.class)
class FamilyCaregiverProfileIT {

	private static final String PATH = "/api/caregivers/";
	private static final String PASSWORD = "test-password";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 0, 30);

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyCaregiverProfileIT.class, "+05:00");
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
		Clock familyCaregiverProfileClock() {
			return Clock.fixed(Instant.parse("2026-09-27T16:30:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void prepareIsolatedProfiles() {
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
					INSERT INTO caregiver (id, user_id, full_name, phone, sector, dialects, status)
					VALUES (?, ?, ?, '+65 80000000', 'Private sector', ' English , Mandarin ', 'AVAILABLE')
					""", id, id + 1000, "Test caregiver " + id);
		}
		seedBinding(101, 42, "FULL", null);
		seedBinding(102, 42, "READ_ONLY", NOW.plusSeconds(1));
		seedBinding(110, 7, "FULL", null);
		seedVisit(301, 101, 201, "2026-09-28T10:00:00", "SCHEDULED");
		seedVisit(302, 102, 202, "2026-09-28T11:00:00", "SCHEDULED");
		seedVisit(303, 101, 203, "2020-01-01T10:00:00", "COMPLETED");
		seedVisit(304, 110, 204, "2026-09-28T10:00:00", "SCHEDULED");
		jdbc.update("UPDATE caregiver SET status = 'INACTIVE' WHERE id = 203");
		jdbc.update("""
				INSERT INTO visit_assignment (visit_id, caregiver_id, status, assigned_at, ended_at)
				VALUES (301, 205, 'REPLACED', '2026-09-25 10:00:00', '2026-09-26 10:00:00')
				""");
	}

	@Test
	void validFamilySessionCanReadOnlyPublicProfileFieldsWithoutCsrf() throws Exception {
		var body = readProfile(loginAs("family-a"), 201);
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("id", "fullName", "dialects");
		assertThat(body.path("id").longValue()).isEqualTo(201);
		assertThat(body.path("fullName").asString()).isEqualTo("Test caregiver 201");
		assertThat(body.path("dialects")).extracting(JsonNode::asString).containsExactly("English", "Mandarin");
	}

	@Test
	void readOnlyBindingAllowsProfileUntilItsSingaporeExpiry() throws Exception {
		var session = loginAs("family-a");
		assertThat(readProfile(session, 202).path("id").longValue()).isEqualTo(202);
		jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 102", NOW);
		mvc.perform(get(PATH + "202").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void historicalVisitAllowsAnInactiveCaregiverWithoutExposingTheirStatus() throws Exception {
		var body = readProfile(loginAs("family-a"), 203);
		assertThat(body.path("id").longValue()).isEqualTo(203);
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("id", "fullName", "dialects");
	}

	@ParameterizedTest
	@ValueSource(strings = { "SCHEDULED", "ARRIVED", "IN_PROGRESS", "COMPLETED", "VERIFIED",
			"AUTO_CLOSED", "EXCEPTION", "CANCELLED" })
	void visitRelationshipsDoNotRequireACertainDateOrStatus(String visitStatus) throws Exception {
		jdbc.update("UPDATE visit SET status = ?, scheduled_start = '2027-01-01 10:00:00' WHERE id = 301", visitStatus);
		assertThat(readProfile(loginAs("family-a"), 201).path("id").longValue()).isEqualTo(201);
	}

	@ParameterizedTest
	@ValueSource(longs = { 204, 205, 206, 999, 0, -1 })
	void unrelatedMissingAndReplacedOnlyProfilesAreForbiddenWithoutDisclosingExistence(long caregiverId)
			throws Exception {
		mvc.perform(get(PATH + caregiverId).session(loginAs("family-a")))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.fullName").doesNotExist());
	}

	@Test
	void sessionIdentityCannotBeReplacedByFamilyUserOrRoleParameters() throws Exception {
		var session = loginAs("family-b");
		assertThat(readProfile(session, 204).path("id").longValue()).isEqualTo(204);
		mvc.perform(get(PATH + "201").session(session)
				.param("familyMemberId", "42").param("userId", "7").param("role", "FAMILY"))
				.andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = { "REVOKED", "PENDING_CONFIRMATION", "REJECTED" })
	void changedBindingStatusImmediatelyRemovesProfileAccess(String bindingStatus) throws Exception {
		var session = loginAs("family-a");
		assertThat(readProfile(session, 201).path("id").longValue()).isEqualTo(201);
		jdbc.update("UPDATE elder_family_binding SET status = ? WHERE elder_id = 101", bindingStatus);
		mvc.perform(get(PATH + "201").session(session)).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1 })
	void expiredBindingsCannotUseAnExistingSession(int secondsAfterNow) throws Exception {
		var session = loginAs("family-a");
		jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE elder_id = 101", NOW.plusSeconds(secondsAfterNow));
		mvc.perform(get(PATH + "201").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void anotherValidBindingRetainsAccessAfterOneRelationshipIsRevoked() throws Exception {
		var session = loginAs("family-a");
		seedVisit(305, 102, 201, "2026-09-29T10:00:00", "SCHEDULED");
		jdbc.update("UPDATE elder_family_binding SET status = 'REVOKED' WHERE elder_id = 101");
		assertThat(readProfile(session, 201).path("id").longValue()).isEqualTo(201);
	}

	@ParameterizedTest
	@ValueSource(strings = { "no-profile", "no-binding", "manager", "caregiver", "elder" })
	void accountsWithoutFamilyProfileAccessAreForbidden(String username) throws Exception {
		mvc.perform(get(PATH + "201").session(loginAs(username))).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = { "disabled", "role-removed", "profile-detached" })
	void currentAccountAndProfileAccessIsRecheckedAfterLogin(String change) throws Exception {
		var session = loginAs("family-a");
		assertThat(readProfile(session, 201).path("id").longValue()).isEqualTo(201);
		switch (change) {
			case "disabled" -> jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = 7");
			case "role-removed" -> jdbc.update("DELETE FROM user_role WHERE user_id = 7 AND role = 'FAMILY'");
			case "profile-detached" -> jdbc.update("UPDATE family_member SET user_id = NULL WHERE id = 42");
			default -> throw new IllegalArgumentException("Unknown account change: " + change);
		}
		mvc.perform(get(PATH + "201").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void anonymousRequestsRequireLogin() throws Exception {
		mvc.perform(get(PATH + "201")).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "not-a-number", "9223372036854775808" })
	void malformedPathIdentifiersReturnBadRequest(String caregiverId) throws Exception {
		mvc.perform(get(PATH + caregiverId).session(loginAs("family-a")))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void dialectsTrimEmptyEntriesWhilePreservingOrderSpellingAndDuplicates() throws Exception {
		jdbc.update("UPDATE caregiver SET dialects = ? WHERE id = 201", " , English,\u2003Hokkien\u2003,,English, mandarin , ");
		var body = readProfile(loginAs("family-a"), 201);
		assertThat(body.path("dialects")).extracting(JsonNode::asString)
				.containsExactly("English", "Hokkien", "English", "mandarin");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", " , , " })
	void missingOrBlankDialectsReturnAnEmptyArray(String dialects) throws Exception {
		jdbc.update("UPDATE caregiver SET dialects = ? WHERE id = 201", dialects);
		var dialectArray = readProfile(loginAs("family-a"), 201).path("dialects");
		assertThat(dialectArray.isArray()).isTrue();
		assertThat(dialectArray).isEmpty();
	}

	private JsonNode readProfile(MockHttpSession session, long caregiverId) throws Exception {
		var response = mvc.perform(get(PATH + caregiverId).session(session))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andReturn().getResponse();
		return json.readTree(response.getContentAsString());
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
