package sg.nus.carelink.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import jakarta.servlet.http.Cookie;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import sg.nus.carelink.profile.domain.repository.IntakeApplicationRepository;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * Verifies the FM01 POST contract through the security filters and an isolated MySQL database.
 *
 * @author Wang Zhili
 */
@SpringBootTest
@AutoConfigureMockMvc
class IntakeSubmissionApiIT {

	private static final String PATH = "/api/intake-applications";
	private static final String MINIMUM_REQUEST = """
			{"targetElderName":"  Tan Mei  ","targetAddress":" 12 Example Road ","postalCode":" 123456 "}
			""";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, IntakeSubmissionApiIT.class, "+05:00");
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private IntakeApplicationRepository applications;
	private final JsonMapper json = JsonMapper.builder().build();

	@BeforeEach
	void prepareAccounts() {
		jdbc.update("DELETE FROM intake_application");
		jdbc.update("DELETE FROM family_member");
		jdbc.update("DELETE FROM user_role");
		jdbc.update("DELETE FROM app_user");
		jdbc.update("INSERT INTO app_user (id, username, password_hash, display_name) VALUES "
				+ "(7, 'family-a', '{noop}test-password', 'Family A'), "
				+ "(9, 'family-b', '{noop}test-password', 'Family B'), "
				+ "(10, 'manager', '{noop}test-password', 'Manager'), "
				+ "(12, 'no-profile', '{noop}test-password', 'Missing profile')");
		jdbc.update("INSERT INTO user_role (user_id, role) VALUES "
				+ "(7, 'FAMILY'), (9, 'FAMILY'), (10, 'MANAGER'), (12, 'FAMILY')");
		jdbc.update("INSERT INTO family_member (id, user_id, full_name) VALUES "
				+ "(42, 7, 'Family A'), (7, 9, 'Family B')");
	}

	@Test
	void familyWithoutAnElderBindingCanSubmitAndReceiveItsSavedApplication() throws Exception {
		var response = mvc.perform(post(PATH).with(user("family-a").roles("FAMILY")).with(csrfCookie())
				.contentType(MediaType.APPLICATION_JSON).content(MINIMUM_REQUEST))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.applicantFamilyMemberId").value(42))
				.andExpect(jsonPath("$.targetElderName").value("Tan Mei"))
				.andExpect(jsonPath("$.targetAddress").value("12 Example Road"))
				.andExpect(jsonPath("$.postalCode").value("123456"))
				.andExpect(jsonPath("$.mobilityLevel").value("INDEPENDENT"))
				.andExpect(jsonPath("$.careNeeds").isEmpty())
				.andExpect(jsonPath("$.status").value("SUBMITTED"))
				.andReturn().getResponse();
		var body = json.readTree(response.getContentAsString());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("id", "applicantFamilyMemberId",
				"targetElderName", "targetElderAge", "targetAddress", "postalCode", "mobilityLevel",
				"preferredDialects", "careNeeds", "medicalNotes", "status", "reviewRemarks", "createdAt",
				"reviewedAt", "elderId");
		assertThat(body.path("reviewedAt").isNull()).isTrue();
		assertThat(body.path("reviewRemarks").isNull()).isTrue();
		assertThat(body.path("elderId").isNull()).isTrue();
		var saved = applications.findById(body.path("id").longValue()).orElseThrow();
		assertThat(saved.applicantFamilyMemberId()).isEqualTo(42L);
		assertThat(saved.createdAt()).isNotNull();
	}

	@ParameterizedTest
	@MethodSource("invalidFieldValues")
	void rejectsValuesOutsideTheSubmissionContract(String field, Object value) throws Exception {
		ObjectNode request = (ObjectNode) json.readTree(MINIMUM_REQUEST).deepCopy();
		request.set(field, json.valueToTree(value));
		mvc.perform(post(PATH).with(user("family-a").roles("FAMILY")).with(csrfCookie())
				.contentType(MediaType.APPLICATION_JSON).content(request.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.fields").isNotEmpty());
	}

	private static Stream<Arguments> invalidFieldValues() {
		return Stream.of(
				Arguments.of("targetElderName", " \t\n "),
				Arguments.of("targetAddress", " "),
				Arguments.of("postalCode", " "),
				Arguments.of("targetElderName", "N".repeat(101)),
				Arguments.of("targetAddress", "A".repeat(256)),
				Arguments.of("postalCode", "1".repeat(11)),
				Arguments.of("preferredDialects", "D".repeat(101)),
				Arguments.of("targetElderAge", -1),
				Arguments.of("careNeeds", List.of("BATHING", "BATHING")),
				Arguments.of("careNeeds", List.of("")));
	}

	@ParameterizedTest
	@CsvSource({ "targetElderName,100", "targetAddress,255", "postalCode,10", "preferredDialects,100" })
	void acceptsUnicodeCharactersAtTheFieldLengthLimit(String field, int limit) throws Exception {
		String value = "𠮷".repeat(limit);
		ObjectNode request = (ObjectNode) json.readTree(MINIMUM_REQUEST);
		request.put(field, value);
		var response = mvc.perform(post(PATH).with(user("family-a").roles("FAMILY")).with(csrfCookie())
				.contentType(MediaType.APPLICATION_JSON).content(request.toString()))
				.andExpect(status().isCreated()).andReturn().getResponse();
		var body = json.readTree(response.getContentAsString());
		assertThat(body.path(field).stringValue()).isEqualTo(value);
		var saved = applications.findById(body.path("id").longValue()).orElseThrow();
		assertThat(json.valueToTree(saved).path(field).stringValue()).isEqualTo(value);
	}

	@ParameterizedTest
	@CsvSource({ "targetElderName,100", "targetAddress,255", "postalCode,10", "preferredDialects,100" })
	void rejectsUnicodeCharactersBeyondTheFieldLengthLimit(String field, int limit) throws Exception {
		ObjectNode request = (ObjectNode) json.readTree(MINIMUM_REQUEST);
		request.put(field, "𠮷".repeat(limit + 1));
		mvc.perform(post(PATH).with(user("family-a").roles("FAMILY")).with(csrfCookie())
				.contentType(MediaType.APPLICATION_JSON).content(request.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields." + field).isNotEmpty());
	}

	@ParameterizedTest
	@MethodSource("invalidJsonRequests")
	void rejectsMalformedMissingOrUnexpectedJsonFields(String request) throws Exception {
		mvc.perform(post(PATH).with(user("family-a").roles("FAMILY")).with(csrfCookie())
				.contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
	}

	private static Stream<String> invalidJsonRequests() {
		var mapper = JsonMapper.builder().build();
		Stream<String> invalidProperties = Stream.of(
				"\"applicantFamilyMemberId\":7", "\"userId\":9", "\"id\":10",
				"\"status\":\"APPROVED\"", "\"reviewedByUserId\":10", "\"reviewRemarks\":\"Approved\"",
				"\"reviewedAt\":\"2026-09-19T10:00:00Z\"", "\"elderId\":123",
				"\"createdAt\":\"2026-09-19T10:00:00Z\"", "\"unexpectedField\":true",
				"\"mobilityLevel\":\"FLYING\"", "\"mobilityLevel\":1", "\"targetElderAge\":80.5",
				"\"targetElderAge\":\"80\"", "\"targetElderAge\":2147483648",
				"\"preferredDialects\":123", "\"careNeeds\":[1]", "\"careNeeds\":[null]",
				"\"careNeeds\":\"BATHING\"", "\"medicalNotes\":true",
				"\"targetElderAge\":null", "\"mobilityLevel\":null", "\"careNeeds\":null",
				"\"preferredDialects\":null", "\"medicalNotes\":null")
				.map(property -> MINIMUM_REQUEST.strip().replaceFirst("}$", "," + property + "}"));
		Stream<String> missingRequired = Stream.of("targetElderName", "targetAddress", "postalCode")
				.flatMap(field -> {
					ObjectNode missing = (ObjectNode) mapper.readTree(MINIMUM_REQUEST);
					missing.remove(field);
					ObjectNode explicitNull = (ObjectNode) mapper.readTree(MINIMUM_REQUEST);
					explicitNull.putNull(field);
					return Stream.of(missing.toString(), explicitNull.toString());
				});
		return Stream.concat(Stream.concat(invalidProperties, missingRequired),
				Stream.of("{", "", "null", "[]"));
	}

	@Test
	void browserCanLoginAndSubmitUsingTheSessionAndRawCsrfCookie() throws Exception {
		BrowserLogin login = loginAs("family-b");
		mvc.perform(post(PATH).session(login.session()).cookie(login.csrfCookie())
				.header("X-XSRF-TOKEN", login.csrfCookie().getValue())
				.contentType(MediaType.APPLICATION_JSON).content(MINIMUM_REQUEST))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.applicantFamilyMemberId").value(7));
	}

	@Test
	void anonymousSubmissionWithValidCsrfIsRejectedBySharedSecurity() throws Exception {
		mvc.perform(post(PATH).with(csrfCookie()).contentType(MediaType.APPLICATION_JSON).content(MINIMUM_REQUEST))
				.andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "manager", "no-profile", "unknown" })
	void refusesAccountsWithoutCurrentFamilyAccess(String username) throws Exception {
		mvc.perform(post(PATH).with(user(username).roles(username.equals("manager") ? "MANAGER" : "FAMILY"))
				.with(csrfCookie()).contentType(MediaType.APPLICATION_JSON).content(MINIMUM_REQUEST))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void disabledAccountCannotSubmitThroughAnExistingSession() throws Exception {
		BrowserLogin login = loginAs("family-a");
		jdbc.update("UPDATE app_user SET enabled = false WHERE id = 7");
		mvc.perform(post(PATH).session(login.session()).cookie(login.csrfCookie())
				.header("X-XSRF-TOKEN", login.csrfCookie().getValue())
				.contentType(MediaType.APPLICATION_JSON).content(MINIMUM_REQUEST))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@ParameterizedTest
	@ValueSource(strings = { "missing", "incorrect", "missing-cookie" })
	void refusesSubmissionWithoutMatchingCsrfCookieAndHeader(String mode) throws Exception {
		BrowserLogin login = loginAs("family-a");
		var request = post(PATH).session(login.session()).contentType(MediaType.APPLICATION_JSON).content(MINIMUM_REQUEST);
		if (!mode.equals("missing-cookie")) {
			request.cookie(login.csrfCookie());
		}
		if (!mode.equals("missing")) {
			request.header("X-XSRF-TOKEN", mode.equals("incorrect") ? "invalid-token" : login.csrfCookie().getValue());
		}
		mvc.perform(request).andExpect(status().isForbidden());
	}

	@Test
	void acceptsOptionalDetailsAndInclusiveLimitsWithoutInventingExtraRestrictions() throws Exception {
		var request = json.writeValueAsString(Map.of(
				"targetElderName", " " + "N".repeat(100) + " ",
				"targetAddress", "A".repeat(255), "postalCode", "1".repeat(10), "targetElderAge", 0,
				"mobilityLevel", "WHEELCHAIR_BEDBOUND", "preferredDialects", "D".repeat(100),
				"careNeeds", List.of("BATHING", "Reminder: \"water\"", " "), "medicalNotes", "Needs assistance"));
		mvc.perform(post(PATH).with(user("family-a").roles("FAMILY")).with(csrfCookie())
				.contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.targetElderName").value("N".repeat(100)))
				.andExpect(jsonPath("$.targetElderAge").value(0))
				.andExpect(jsonPath("$.mobilityLevel").value("WHEELCHAIR_BEDBOUND"))
				.andExpect(jsonPath("$.preferredDialects").value("D".repeat(100)))
				.andExpect(jsonPath("$.careNeeds[1]").value("Reminder: \"water\""))
				.andExpect(jsonPath("$.careNeeds[2]").value(" "))
				.andExpect(jsonPath("$.medicalNotes").value("Needs assistance"));
	}

	@Test
	void returnsAnUnambiguousCreationTimeEvenWhenTheDatabaseUsesAnotherTimeZone() throws Exception {
		Instant before = Instant.now().minusSeconds(1);
		var response = mvc.perform(post(PATH).with(user("family-a").roles("FAMILY")).with(csrfCookie())
				.contentType(MediaType.APPLICATION_JSON).content(MINIMUM_REQUEST))
				.andExpect(status().isCreated()).andReturn().getResponse();
		var createdAt = OffsetDateTime.parse(json.readTree(response.getContentAsString()).path("createdAt").stringValue());
		assertThat(createdAt.toInstant()).isBetween(before, Instant.now().plusSeconds(1));
	}

	@Test
	void failedInsertLeavesTheListEmptyAndAFollowingSubmissionCanSucceed() throws Exception {
		jdbc.execute("ALTER TABLE intake_application ADD CONSTRAINT fm01_test_failure "
				+ "CHECK (target_elder_name <> 'Tan Mei')");
		try {
			var response = mvc.perform(post(PATH).with(user("family-a").roles("FAMILY")).with(csrfCookie())
					.contentType(MediaType.APPLICATION_JSON).content(MINIMUM_REQUEST))
					.andExpect(status().isInternalServerError())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.status").value(500))
					.andExpect(jsonPath("$.id").doesNotExist()).andReturn().getResponse();
			assertThat(response.getContentAsString()).doesNotContain("fm01_test_failure", "INSERT", "Tan Mei");
			mvc.perform(get(PATH).with(user("family-a").roles("FAMILY")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items").isEmpty())
					.andExpect(jsonPath("$.totalElements").value(0));
		} finally {
			jdbc.execute("ALTER TABLE intake_application DROP CHECK fm01_test_failure");
		}
		mvc.perform(post(PATH).with(user("family-a").roles("FAMILY")).with(csrfCookie())
				.contentType(MediaType.APPLICATION_JSON).content(MINIMUM_REQUEST))
				.andExpect(status().isCreated());
		mvc.perform(get(PATH).with(user("family-a").roles("FAMILY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].targetElderName").value("Tan Mei"))
				.andExpect(jsonPath("$.items[0].status").value("SUBMITTED"));
	}

	private BrowserLogin loginAs(String username) throws Exception {
		Cookie token = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		assertThat(token.isHttpOnly()).isFalse();
		var result = mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("username", username, "password", "test-password"))))
				.andExpect(status().isOk()).andReturn();
		return new BrowserLogin((MockHttpSession) result.getRequest().getSession(false), token);
	}

	private RequestPostProcessor csrfCookie() throws Exception {
		Cookie token = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		return request -> {
			request.setCookies(token);
			request.addHeader("X-XSRF-TOKEN", token.getValue());
			return request;
		};
	}

	private record BrowserLogin(MockHttpSession session, Cookie csrfCookie) {
	}
}
