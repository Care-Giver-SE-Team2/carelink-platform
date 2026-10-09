package sg.nus.carelink.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
 * Verifies elder list access through real login sessions and isolated MySQL data.
 *
 * @author Wang Zhili
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FamilyElderListIT.FixedTime.class)
class FamilyElderListIT {

	private static final String PATH = "/api/elders";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 10, 0);
	private static final List<Long> ELDER_IDS = List.of(101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 110L);

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyElderListIT.class, "+05:00");
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
		Clock familyElderListClock() {
			return Clock.fixed(Instant.parse("2026-09-23T02:00:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void prepareIsolatedRelationships() {
		jdbc.update("DELETE FROM care_plan_node");
		jdbc.update("DELETE FROM care_plan");
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
				(13, 'no-active-binding', '{noop}test-password', 'Family without access'),
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
		for (long id : ELDER_IDS) {
			jdbc.update("""
					INSERT INTO elder (id, full_name, date_of_birth, address, sector, phone, medical_notes)
					VALUES (?, ?, '1945-01-02', '12 Example Road', 'North', '99998888', 'Private medical note')
					""", id, "Test elder " + id);
		}
		seedBinding(101, 42, "FULL", "ACTIVE", null);
		seedBinding(102, 42, "READ_ONLY", "ACTIVE", NOW.plusSeconds(1));
		seedBinding(103, 42, "FULL", "ACTIVE", NOW);
		seedBinding(104, 42, "FULL", "ACTIVE", NOW.minusSeconds(1));
		seedBinding(105, 42, "FULL", "PENDING_CONFIRMATION", null);
		seedBinding(106, 42, "FULL", "REJECTED", null);
		seedBinding(107, 42, "FULL", "REVOKED", null);
		seedBinding(110, 7, "FULL", "ACTIVE", null);
		seedBinding(108, 55, "FULL", "PENDING_CONFIRMATION", null);
		jdbc.update("""
				INSERT INTO care_plan (id, elder_id, version, status, start_date) VALUES
				(201, 101, 3, 'PUBLISHED', '2099-01-01'),
				(202, 102, 2, 'DRAFT', NULL),
				(203, 110, 4, 'STOPPED', '2026-01-01')
				""");
		jdbc.update("""
				INSERT INTO care_plan_node (id, care_plan_id, name, schedule_days)
				VALUES (301, 201, 'Daily care', 'DAILY')
				""");
	}

	@Test
	void familySessionListsOnlyActiveUnexpiredBindingsWithTheSharedResponseFields() throws Exception {
		var response = mvc.perform(get(PATH).session(loginAs("family-a")))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$[?(@.id == 101)].planStatus").value(contains("published")))
				.andExpect(jsonPath("$[?(@.id == 101)].planVersion").value(contains(3)))
				.andExpect(jsonPath("$[?(@.id == 101)].nextVisitDate").value(contains("2099-01-01")))
				.andExpect(jsonPath("$[?(@.id == 102)].planStatus").value(contains("draft")))
				.andExpect(jsonPath("$[?(@.id == 102)].planVersion").value(contains(2)))
				.andReturn().getResponse();
		var body = json.readTree(response.getContentAsString());
		assertThat(body.isArray()).isTrue();
		assertThat(body).extracting(item -> item.path("id").longValue()).containsExactlyInAnyOrder(101L, 102L);
		assertListFields(body);
		assertThat(body).allSatisfy(item -> {
			assertThat(item.path("dateOfBirth").asString()).isEqualTo("1945-01-02");
			assertThat(item.path("address").asString()).isEqualTo("12 Example Road");
			assertThat(item.path("sector").asString()).isEqualTo("North");
		});
	}

	@Test
	void suppliedIdentityAndRoleParametersCannotReadAnotherFamilysElders() throws Exception {
		var response = mvc.perform(get(PATH).session(loginAs("family-b"))
				.param("familyMemberId", "42").param("userId", "7").param("role", "MANAGER"))
				.andExpect(status().isOk()).andReturn().getResponse();
		var body = json.readTree(response.getContentAsString());
		assertThat(body).extracting(item -> item.path("id").longValue()).containsExactly(110L);
		assertThat(body.get(0).path("planStatus").asString()).isEqualTo("stopped");
		assertThat(body.get(0).path("planVersion").asInt()).isEqualTo(4);
		assertThat(body.get(0).path("nextVisitDate").isNull()).isTrue();
	}

	@Test
	void familyWithoutReadableBindingsGetsAnEmptyArray() throws Exception {
		mvc.perform(get(PATH).session(loginAs("no-active-binding")))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
	}

	@Test
	void anonymousRequestsRequireLogin() throws Exception {
		mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "no-profile", "caregiver", "elder" })
	void accountsWithoutFamilyOrManagerAccessAreForbidden(String username) throws Exception {
		mvc.perform(get(PATH).session(loginAs(username)))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(403));
	}

	@ParameterizedTest
	@ValueSource(strings = { "disabled", "role-removed" })
	void currentAccountAccessIsRecheckedForAnExistingFamilySession(String change) throws Exception {
		var session = loginAs("family-a");
		if ("disabled".equals(change)) {
			jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = 7");
		} else {
			jdbc.update("DELETE FROM user_role WHERE user_id = 7 AND role = 'FAMILY'");
		}
		mvc.perform(get(PATH).session(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void revocationRemovesTheElderFromTheNextRequestInTheSameSession() throws Exception {
		var session = loginAs("family-a");
		mvc.perform(get(PATH).session(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
		jdbc.update("UPDATE elder_family_binding SET status = 'REVOKED' WHERE elder_id = 101 AND family_member_id = 42");
		mvc.perform(get(PATH).session(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(102));
	}

	@Test
	void managerRetainsAllEldersAndTheSameArrayContract() throws Exception {
		var response = mvc.perform(get(PATH).session(loginAs("manager")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == 101)].planStatus").value(contains("published")))
				.andExpect(jsonPath("$[?(@.id == 101)].nextVisitDate").value(contains("2099-01-01")))
				.andReturn().getResponse();
		var body = json.readTree(response.getContentAsString());
		assertThat(body.isArray()).isTrue();
		assertThat(body).extracting(item -> item.path("id").longValue()).containsExactlyInAnyOrderElementsOf(ELDER_IDS);
		assertListFields(body);
	}

	@Test
	void elderDetailRemainsRestrictedToManagersEvenForAnActivelyBoundFamily() throws Exception {
		mvc.perform(get(PATH + "/101").session(loginAs("family-a")))
				.andExpect(status().isForbidden());
		mvc.perform(get(PATH + "/101").session(loginAs("manager")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(101));
	}

	private static void assertListFields(JsonNode body) {
		assertThat(body).isNotEmpty().allSatisfy(item -> assertThat(item.propertyNames()).containsExactlyInAnyOrder(
				"id", "fullName", "dateOfBirth", "address", "sector", "planStatus", "planVersion", "nextVisitDate",
				"primaryCaregiverId", "primaryCaregiverName", "primaryCaregiverAssignedAt"));
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
