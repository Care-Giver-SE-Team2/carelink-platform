package sg.nus.carelink.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.testsupport.SharedMySql;

/**
 * Verifies the FM01 list contract through real sessions and an isolated MySQL database.
 *
 * @author Wang Zhili
 */
@SpringBootTest
@AutoConfigureMockMvc
class IntakeListingApiIT {

	private static final String PATH = "/api/intake-applications";
	private static final List<String> STATUSES = List.of("SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED");

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, IntakeListingApiIT.class, "+05:00");
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	private final JsonMapper json = JsonMapper.builder().build();

	@BeforeEach
	void prepareAccounts() {
		jdbc.update("DELETE FROM intake_application");
		jdbc.update("DELETE FROM elder");
		jdbc.update("DELETE FROM family_member");
		jdbc.update("DELETE FROM user_role");
		jdbc.update("DELETE FROM app_user");
		jdbc.update("INSERT INTO app_user (id, username, password_hash, display_name) VALUES "
				+ "(7, 'family-a', '{noop}test-password', 'Family A'), "
				+ "(9, 'family-b', '{noop}test-password', 'Family B'), "
				+ "(10, 'manager', '{noop}test-password', 'Manager'), "
				+ "(12, 'no-profile', '{noop}test-password', 'Missing profile'), "
				+ "(15, 'family-manager', '{noop}test-password', 'Family manager')");
		jdbc.update("INSERT INTO user_role (user_id, role) VALUES "
				+ "(7, 'FAMILY'), (9, 'FAMILY'), (10, 'MANAGER'), (12, 'FAMILY'), "
				+ "(15, 'FAMILY'), (15, 'MANAGER')");
		jdbc.update("INSERT INTO family_member (id, user_id, full_name) VALUES "
				+ "(42, 7, 'Family A'), (7, 9, 'Family B'), (99, 15, 'Family manager')");
	}

	@Test
	void realSessionCanReadDefaultPageWithoutCsrfOrAnElderBinding() throws Exception {
		for (long id = 101; id <= 125; id++) {
			seedApplication(id, 42, "SUBMITTED", "2026-09-20 10:00:00");
		}
		seedApplication(901, 7, "SUBMITTED", "2026-09-21 10:00:00");
		var response = mvc.perform(get(PATH).session(loginAs("family-a").session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(25))
				.andExpect(jsonPath("$.items.length()").value(20))
				.andExpect(jsonPath("$.items[0].id").value(125))
				.andExpect(jsonPath("$.items[19].id").value(106))
				.andReturn().getResponse();
		var body = json.readTree(response.getContentAsString());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("items", "page", "size", "totalElements");
		assertThat(body.path("items")).allSatisfy(item ->
				assertThat(item.path("applicantFamilyMemberId").longValue()).isEqualTo(42L));
	}

	@Test
	void filtersOwnerAndStatusBeforePaginationAndCountsWithStableNewestFirstOrdering() throws Exception {
		seedApplication(101, 42, "SUBMITTED", "2026-09-01 10:00:00");
		seedApplication(102, 42, "SUBMITTED", "2026-09-02 10:00:00");
		seedApplication(103, 42, "SUBMITTED", "2026-09-02 10:00:00");
		seedApplication(104, 42, "REJECTED", "2026-09-03 10:00:00");
		seedApplication(901, 7, "SUBMITTED", "2026-09-20 10:00:00");
		seedApplication(902, 7, "SUBMITTED", "2026-09-19 10:00:00");
		var session = loginAs("family-a").session();
		mvc.perform(get(PATH).session(session).param("status", "SUBMITTED").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].id").value(103))
				.andExpect(jsonPath("$.items[1].id").value(102))
				.andExpect(jsonPath("$.totalElements").value(3));
		mvc.perform(get(PATH).session(session).param("status", "SUBMITTED").param("size", "2").param("page", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].id").value(101))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(3));
		mvc.perform(get(PATH).session(session).param("status", "SUBMITTED").param("size", "2").param("page", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.totalElements").value(3));
		mvc.perform(get(PATH).session(session).param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].id").value(104))
				.andExpect(jsonPath("$.items[1].id").value(103))
				.andExpect(jsonPath("$.totalElements").value(4));
	}

	@ParameterizedTest
	@ValueSource(strings = { "SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED" })
	void acceptsEachDeclaredStatus(String filter) throws Exception {
		for (int index = 0; index < STATUSES.size(); index++) {
			seedApplication(101 + index, 42, STATUSES.get(index), "2026-09-20 10:00:00");
			seedApplication(901 + index, 7, STATUSES.get(index), "2026-09-21 10:00:00");
		}
		mvc.perform(get(PATH).session(loginAs("family-a").session()).param("status", filter))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].status").value(filter))
				.andExpect(jsonPath("$.items[0].applicantFamilyMemberId").value(42));
	}

	@Test
	void emptyFamilyListDoesNotExposeAnotherApplicantsCount() throws Exception {
		seedApplication(901, 7, "SUBMITTED", "2026-09-20 10:00:00");
		mvc.perform(get(PATH).session(loginAs("family-a").session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void acceptsMaximumPageSize() throws Exception {
		seedApplication(101, 42, "SUBMITTED", "2026-09-20 10:00:00");
		mvc.perform(get(PATH).session(loginAs("family-a").session()).param("size", "200"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(200))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void largeValidPageReturnsEmptyItemsAndTheFilteredTotal() throws Exception {
		seedApplication(101, 42, "SUBMITTED", "2026-09-20 10:00:00");
		seedApplication(102, 42, "REJECTED", "2026-09-20 10:00:00");
		seedApplication(901, 7, "SUBMITTED", "2026-09-20 10:00:00");
		mvc.perform(get(PATH).session(loginAs("family-a").session())
				.param("page", "2147483647").param("size", "200").param("status", "SUBMITTED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.page").value(Integer.MAX_VALUE))
				.andExpect(jsonPath("$.size").value(200))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@ParameterizedTest
	@MethodSource("invalidQueries")
	void rejectsInvalidQueryParameters(String parameter, String value) throws Exception {
		mvc.perform(get(PATH).session(loginAs("family-a").session()).param(parameter, value))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
	}

	private static Stream<Arguments> invalidQueries() {
		return Stream.of(Arguments.of("page", "-1"), Arguments.of("page", "1.5"),
				Arguments.of("page", "text"), Arguments.of("page", "2147483648"),
				Arguments.of("size", "0"), Arguments.of("size", "-1"), Arguments.of("size", "201"),
				Arguments.of("size", "1.5"), Arguments.of("size", "text"),
				Arguments.of("size", "2147483648"), Arguments.of("status", "UNKNOWN"),
				Arguments.of("status", "submitted"), Arguments.of("status", ""));
	}

	@Test
	void anonymousListRequestIsUnauthorized() throws Exception {
		mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "manager", "no-profile" })
	void refusesLoggedInAccountsWithoutFamilyAccess(String username) throws Exception {
		mvc.perform(get(PATH).session(loginAs(username).session()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@ParameterizedTest
	@ValueSource(strings = { "disabled", "role-removed", "profile-removed" })
	void rechecksCurrentAccountAccessForAnExistingSession(String change) throws Exception {
		var session = loginAs("family-a").session();
		switch (change) {
			case "disabled" -> jdbc.update("UPDATE app_user SET enabled = false WHERE id = 7");
			case "role-removed" -> jdbc.update("DELETE FROM user_role WHERE user_id = 7 AND role = 'FAMILY'");
			case "profile-removed" -> jdbc.update("DELETE FROM family_member WHERE id = 42");
			default -> throw new IllegalArgumentException("Unexpected account change: " + change);
		}
		mvc.perform(get(PATH).session(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void additionalRolesAndSuppliedIdentityParametersCannotBroadenTheFamilyList() throws Exception {
		seedApplication(101, 99, "SUBMITTED", "2026-09-20 10:00:00");
		seedApplication(901, 42, "SUBMITTED", "2026-09-21 10:00:00");
		mvc.perform(get(PATH).session(loginAs("family-manager").session())
				.param("applicantFamilyMemberId", "42").param("userId", "7"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].id").value(101))
				.andExpect(jsonPath("$.items[0].applicantFamilyMemberId").value(99));
	}

	@Test
	void exposesOnlyFamilyFieldsWithNullableReviewDetailsAndUtcTimestamps() throws Exception {
		seedApplication(101, 42, "SUBMITTED", "2026-09-19 10:00:00");
		seedApplication(102, 42, "APPROVED", "2026-09-20 10:00:00");
		jdbc.update("INSERT INTO elder (id, full_name) VALUES (301, 'Approved elder')");
		jdbc.update("UPDATE intake_application SET target_elder_age = 80, mobility_level = 'ASSISTIVE_CANE', "
				+ "preferred_dialects = 'Mandarin', care_needs = '[\"BATHING\",\"VITALS\"]', "
				+ "medical_notes = 'Needs assistance', reviewed_by_user_id = 10, review_remarks = 'Approved after review', "
				+ "reviewed_at = '2026-09-20 11:00:00', elder_id = 301 WHERE id = 102");
		var response = mvc.perform(get(PATH).session(loginAs("family-a").session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].targetElderAge").value(80))
				.andExpect(jsonPath("$.items[0].mobilityLevel").value("ASSISTIVE_CANE"))
				.andExpect(jsonPath("$.items[0].preferredDialects").value("Mandarin"))
				.andExpect(jsonPath("$.items[0].careNeeds.length()").value(2))
				.andExpect(jsonPath("$.items[0].careNeeds[0]").value("BATHING"))
				.andExpect(jsonPath("$.items[0].careNeeds[1]").value("VITALS"))
				.andExpect(jsonPath("$.items[0].medicalNotes").value("Needs assistance"))
				.andExpect(jsonPath("$.items[0].reviewRemarks").value("Approved after review"))
				.andExpect(jsonPath("$.items[0].createdAt").value("2026-09-20T10:00:00Z"))
				.andExpect(jsonPath("$.items[0].reviewedAt").value("2026-09-20T11:00:00Z"))
				.andExpect(jsonPath("$.items[0].elderId").value(301))
				.andReturn().getResponse();
		var items = json.readTree(response.getContentAsString()).path("items");
		assertThat(items).allSatisfy(item -> assertThat(item.propertyNames()).containsExactlyInAnyOrder(
				"id", "applicantFamilyMemberId", "targetElderName", "targetElderAge", "targetAddress", "postalCode",
				"mobilityLevel", "preferredDialects", "careNeeds", "medicalNotes", "status", "reviewRemarks",
				"createdAt", "reviewedAt", "elderId"));
		var pending = items.get(1);
		assertThat(pending.path("reviewedAt").isNull()).isTrue();
		assertThat(pending.path("reviewRemarks").isNull()).isTrue();
		assertThat(pending.path("elderId").isNull()).isTrue();
	}

	@Test
	void submittedApplicationImmediatelyAppearsInTheSameFamilysList() throws Exception {
		var login = loginAs("family-b");
		var submitted = mvc.perform(post(PATH).session(login.session()).cookie(login.csrfCookie())
				.header("X-XSRF-TOKEN", login.csrfCookie().getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"targetElderName":"Tan Mei","targetAddress":"12 Example Road","postalCode":"123456"}
						"""))
				.andExpect(status().isCreated()).andReturn().getResponse();
		var listed = mvc.perform(get(PATH).session(login.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andReturn().getResponse();
		assertThat(json.readTree(listed.getContentAsString()).path("items").get(0))
				.isEqualTo(json.readTree(submitted.getContentAsString()));
	}

	private void seedApplication(long id, long familyMemberId, String applicationStatus, String createdAt) {
		jdbc.update("INSERT INTO intake_application (id, applicant_family_member_id, target_elder_name, "
				+ "target_address, postal_code, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
				id, familyMemberId, "Elder " + id, "12 Example Road", "123456", applicationStatus, createdAt);
	}

	private BrowserLogin loginAs(String username) throws Exception {
		Cookie token = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		var result = mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("username", username, "password", "test-password"))))
				.andExpect(status().isOk()).andReturn();
		var session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session).isNotNull();
		return new BrowserLogin(session, token);
	}

	private record BrowserLogin(MockHttpSession session, Cookie csrfCookie) {
	}
}
