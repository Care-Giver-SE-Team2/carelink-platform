package sg.nus.carelink.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
 * Verifies the FM01 detail contract through real sessions and an isolated MySQL database.
 *
 * @author Wang Zhili
 */
@SpringBootTest
@AutoConfigureMockMvc
class IntakeDetailApiIT {

	private static final String PATH = "/api/intake-applications";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, IntakeDetailApiIT.class, "+05:00");
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
	void submittedApplicationRemainsInTheOwnersListAndDetailsAcrossLoginSessions() throws Exception {
		seedApplication(102, 7, "SUBMITTED");
		var login = loginAs("family-b");
		var response = mvc.perform(get(PATH + "/102").session(login.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applicantFamilyMemberId").value(7))
				.andReturn().getResponse();
		var application = json.readTree(response.getContentAsString());
		mvc.perform(get(PATH).session(login.session()).param("status", "SUBMITTED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].id").value(application.path("id").longValue()))
				.andExpect(jsonPath("$.items[0].applicantFamilyMemberId").value(7));

		mvc.perform(post("/api/auth/logout").session(login.session()).cookie(login.csrfCookie())
				.header("X-XSRF-TOKEN", login.csrfCookie().getValue()))
				.andExpect(status().isNoContent());
		assertThat(login.session().isInvalid()).isTrue();
		mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
		mvc.perform(get(PATH + "/" + application.path("id").longValue()))
				.andExpect(status().isUnauthorized());

		var otherFamily = loginAs("family-a");
		mvc.perform(get(PATH).session(otherFamily.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.totalElements").value(0));
		mvc.perform(get(PATH + "/" + application.path("id").longValue()).session(otherFamily.session()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.targetElderName").doesNotExist());

		var owner = loginAs("family-b");
		var reloaded = mvc.perform(get(PATH + "/" + application.path("id").longValue()).session(owner.session()))
				.andExpect(status().isOk()).andReturn().getResponse();
		assertThat(json.readTree(reloaded.getContentAsString())).isEqualTo(application);
	}

	@ParameterizedTest
	@ValueSource(strings = { "SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED" })
	void returnsSavedDetailsAndReviewProgressForEachStatus(String applicationStatus) throws Exception {
		seedApplication(101, 42, applicationStatus);
		jdbc.update("UPDATE intake_application SET target_elder_age = 80, mobility_level = 'ASSISTIVE_CANE', "
				+ "preferred_dialects = 'Mandarin', care_needs = '[\"BATHING\",\"VITALS\"]', "
				+ "medical_notes = 'Needs assistance' WHERE id = 101");
		boolean reviewed = applicationStatus.equals("APPROVED") || applicationStatus.equals("REJECTED");
		if (reviewed) {
			jdbc.update("UPDATE intake_application SET reviewed_by_user_id = 10, review_remarks = ?, "
					+ "reviewed_at = '2026-09-20 11:00:00' WHERE id = 101", "Review result: " + applicationStatus);
		}
		if (applicationStatus.equals("APPROVED")) {
			jdbc.update("INSERT INTO elder (id, full_name) VALUES (301, 'Approved elder')");
			jdbc.update("UPDATE intake_application SET elder_id = 301 WHERE id = 101");
		}
		var response = mvc.perform(get(PATH + "/101").session(loginAs("family-a").session()))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(101))
				.andExpect(jsonPath("$.applicantFamilyMemberId").value(42))
				.andExpect(jsonPath("$.targetElderName").value("Elder 101"))
				.andExpect(jsonPath("$.targetElderAge").value(80))
				.andExpect(jsonPath("$.targetAddress").value("12 Example Road"))
				.andExpect(jsonPath("$.postalCode").value("123456"))
				.andExpect(jsonPath("$.mobilityLevel").value("ASSISTIVE_CANE"))
				.andExpect(jsonPath("$.preferredDialects").value("Mandarin"))
				.andExpect(jsonPath("$.careNeeds.length()").value(2))
				.andExpect(jsonPath("$.careNeeds[0]").value("BATHING"))
				.andExpect(jsonPath("$.careNeeds[1]").value("VITALS"))
				.andExpect(jsonPath("$.medicalNotes").value("Needs assistance"))
				.andExpect(jsonPath("$.status").value(applicationStatus))
				.andExpect(jsonPath("$.createdAt").value("2026-09-20T10:00:00Z"))
				.andReturn().getResponse();
		var body = json.readTree(response.getContentAsString());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("id", "applicantFamilyMemberId",
				"targetElderName", "targetElderAge", "targetAddress", "postalCode", "mobilityLevel",
				"preferredDialects", "careNeeds", "medicalNotes", "status", "reviewRemarks", "createdAt",
				"reviewedAt", "elderId");
		if (reviewed) {
			assertThat(body.path("reviewRemarks").stringValue()).isEqualTo("Review result: " + applicationStatus);
			assertThat(body.path("reviewedAt").stringValue()).isEqualTo("2026-09-20T11:00:00Z");
		} else {
			assertThat(body.path("reviewRemarks").isNull()).isTrue();
			assertThat(body.path("reviewedAt").isNull()).isTrue();
		}
		if (applicationStatus.equals("APPROVED")) {
			assertThat(body.path("elderId").longValue()).isEqualTo(301L);
		} else {
			assertThat(body.path("elderId").isNull()).isTrue();
		}
	}

	@Test
	void anotherFamilysApplicationReturnsForbiddenWithoutApplicationDetails() throws Exception {
		seedApplication(901, 7, "SUBMITTED");
		var response = mvc.perform(get(PATH + "/901").session(loginAs("family-a").session()))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.id").doesNotExist())
				.andExpect(jsonPath("$.targetElderName").doesNotExist())
				.andReturn().getResponse();
		assertThat(response.getContentAsString()).doesNotContain("Elder 901", "12 Example Road", "123456",
				"applicantFamilyMemberId", "reviewRemarks");
	}

	@ParameterizedTest
	@ValueSource(longs = { 999, 0, -1, Long.MAX_VALUE })
	void missingApplicationReturnsNotFoundForAValidInt64Identifier(long applicationId) throws Exception {
		mvc.perform(get(PATH + "/" + applicationId).session(loginAs("family-a").session()))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.title").value("Resource not found"))
				.andExpect(jsonPath("$.targetElderName").doesNotExist());
	}

	@Test
	void anonymousDetailRequestIsUnauthorized() throws Exception {
		mvc.perform(get(PATH + "/101")).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "manager", "no-profile" })
	void refusesLoggedInAccountsWithoutFamilyAccessBeforeLookingUpTheApplication(String username) throws Exception {
		mvc.perform(get(PATH + "/999").session(loginAs(username).session()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@ParameterizedTest
	@ValueSource(strings = { "disabled", "role-removed", "profile-removed" })
	void rechecksCurrentAccessForAnExistingSessionBeforeLookingUpTheApplication(String change) throws Exception {
		var session = loginAs("family-a").session();
		switch (change) {
			case "disabled" -> jdbc.update("UPDATE app_user SET enabled = false WHERE id = 7");
			case "role-removed" -> jdbc.update("DELETE FROM user_role WHERE user_id = 7 AND role = 'FAMILY'");
			case "profile-removed" -> jdbc.update("DELETE FROM family_member WHERE id = 42");
			default -> throw new IllegalArgumentException("Unexpected account change: " + change);
		}
		mvc.perform(get(PATH + "/999").session(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void anAdditionalManagerRoleDoesNotBroadenFamilyOwnership() throws Exception {
		seedApplication(101, 99, "SUBMITTED");
		seedApplication(901, 42, "SUBMITTED");
		var session = loginAs("family-manager").session();
		mvc.perform(get(PATH + "/101").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applicantFamilyMemberId").value(99));
		mvc.perform(get(PATH + "/901").session(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void queryParametersCannotOverrideThePathIdentifierOrLoggedInFamily() throws Exception {
		seedApplication(101, 42, "SUBMITTED");
		seedApplication(901, 7, "SUBMITTED");
		var session = loginAs("family-a").session();
		mvc.perform(get(PATH + "/101").session(session).param("id", "901")
				.param("applicantFamilyMemberId", "7").param("userId", "9"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(101))
				.andExpect(jsonPath("$.applicantFamilyMemberId").value(42));
		mvc.perform(get(PATH + "/901").session(session).param("id", "101")
				.param("applicantFamilyMemberId", "7").param("userId", "9"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@ParameterizedTest
	@ValueSource(strings = { "text", "1.5", "9223372036854775808", "-9223372036854775809", " " })
	void malformedOrOutOfRangeIdentifiersReturnASafeBadRequest(String applicationId) throws Exception {
		var response = mvc.perform(get(PATH + "/{id}", applicationId).session(loginAs("family-a").session()))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Invalid request"))
				.andReturn().getResponse();
		assertThat(response.getContentAsString()).doesNotContain("java.lang", "NumberFormatException",
				"MethodArgumentTypeMismatchException", "Failed to convert", "sg.nus.carelink");
	}

	private void seedApplication(long id, long familyMemberId, String applicationStatus) {
		jdbc.update("INSERT INTO intake_application (id, applicant_family_member_id, target_elder_name, "
				+ "target_address, postal_code, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
				id, familyMemberId, "Elder " + id, "12 Example Road", "123456", applicationStatus, "2026-09-20 10:00:00");
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
