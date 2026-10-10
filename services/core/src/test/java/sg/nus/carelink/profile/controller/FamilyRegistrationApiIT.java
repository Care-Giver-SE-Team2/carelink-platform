package sg.nus.carelink.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;

import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.testsupport.SharedMySql;

/** Family sign-up through the real security filters and MySQL: sign up, then sign in with family access. */
@SpringBootTest
@AutoConfigureMockMvc
class FamilyRegistrationApiIT {

	private static final String SIGN_UP = """
			{"username":"lim.family","password":"chosen-password","fullName":"Lim Wei Ling","phone":"91234567"}
			""";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyRegistrationApiIT.class, "+08:00");
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	private final JsonMapper json = JsonMapper.builder().build();

	@BeforeEach
	void clearAccounts() {
		jdbc.update("DELETE FROM intake_application");
		jdbc.update("DELETE FROM family_member");
		jdbc.update("DELETE FROM user_role");
		jdbc.update("DELETE FROM app_user");
	}

	@Test
	void anAnonymousVisitorCanSignUpAndSignInWithFamilyAccess() throws Exception {
		Cookie token = csrf();
		mvc.perform(post("/api/family-registrations").cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON).content(SIGN_UP))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("lim.family"));

		assertThat(jdbc.queryForObject("SELECT role FROM user_role r JOIN app_user u ON u.id = r.user_id "
				+ "WHERE u.username = 'lim.family'", String.class)).isEqualTo("FAMILY");
		assertThat(jdbc.queryForObject("SELECT phone FROM family_member f JOIN app_user u ON u.id = f.user_id "
				+ "WHERE u.username = 'lim.family'", String.class)).isEqualTo("+6591234567");

		var login = mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("username", "lim.family", "password", "chosen-password"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles[0]").value("FAMILY"))
				.andReturn();
		var session = (MockHttpSession) login.getRequest().getSession(false);

		// No elder has linked the new account yet, so there is nothing to apply for, but the family pages open.
		mvc.perform(get("/api/family/service-applications").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void aTakenUsernameIsAConflictAndCreatesNothing() throws Exception {
		Cookie token = csrf();
		mvc.perform(post("/api/family-registrations").cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON).content(SIGN_UP))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/family-registrations").cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON).content(SIGN_UP))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_member", Integer.class)).isEqualTo(1);
	}

	@Test
	void signUpStillNeedsCsrf() throws Exception {
		mvc.perform(post("/api/family-registrations").contentType(MediaType.APPLICATION_JSON).content(SIGN_UP))
				.andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isZero();
	}

	private Cookie csrf() throws Exception {
		return mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
	}
}
