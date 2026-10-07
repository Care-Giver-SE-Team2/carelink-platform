package sg.nus.carelink.shared.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import sg.nus.carelink.identity.application.IdentityService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;

import org.springframework.http.MediaType;

/**
 * Exercises the actual {@link SecurityFilterChain} rather than restating them, so a
 * rule that silently stops matching (a typo'd path, a reordered matcher) fails here
 * instead of only being noticed in a browser.
 */
// The slice only needs AuthController (its IdentityService is mocked below). The feature
// modules' controllers would each drag in their service, which is not what this test is
// about, so they are kept out of the slice.
@WebMvcTest(excludeFilters = @ComponentScan.Filter(
		type = FilterType.REGEX, pattern = "sg\\.nus\\.carelink\\.(?!identity\\.).*\\.controller\\..*"))
@Import(SecurityConfig.class)
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	// Neither bean is exercised: they only exist to satisfy SecurityConfig's and
	// AuthController's constructors so the web application context can start.
	@MockitoBean
	private UserDetailsService userDetailsService;

	@MockitoBean
	private IdentityService identityService;

	@Test
	void permitsAnonymousAccessToTheApiDocs() throws Exception {
		mockMvc.perform(get("/docs/index.html")).andExpect(status().isOk());
	}

	@Test
	void permitsAnonymousAccessToTheOpenApiContract() throws Exception {
		mockMvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
	}

	@Test
	void permitsAnonymousAccessToTheDraftOpenApiContract() throws Exception {
		mockMvc.perform(get("/openapi-draft.yaml")).andExpect(status().isOk());
	}

	@Test
	void requiresAuthenticationForEverythingElse() throws Exception {
		mockMvc.perform(get("/api/some-protected-resource")).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = {"/caregiver", "/caregiver/", "/caregiver/visits/42"})
	void caregiverDocumentRoutesForwardWithoutOpeningTheApi(String path) throws Exception {
		mockMvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
		mockMvc.perform(get("/api/caregivers/me/schedule")).andExpect(status().isUnauthorized());
	}

	@Test
	void anonymousBrowserCanInitialiseItsCsrfCookie() throws Exception {
		mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
				.andExpect(cookie().exists("XSRF-TOKEN"))
				.andExpect(cookie().httpOnly("XSRF-TOKEN", false));
	}

	@Test
	void sharedSecurityRejectsRequestsWithoutCsrfProtection() throws Exception {
		mockMvc.perform(post("/api/intake-applications").with(user("family-a").roles("FAMILY")))
				.andExpect(status().isForbidden());
	}

	@Test
	void unreadableJsonUsesTheSameProblemFormatForExistingEndpoints() throws Exception {
		var token = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
		mockMvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
	}
}
