package sg.nus.carelink.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exercises the interceptor on its own, without the Spring context or the
 * security filter chain: the security context is set by hand, exactly as the
 * filter chain would have left it. Wiring (which paths it covers) is checked in
 * ApplicationContextIT against the real application.
 */
class RequestContextInterceptorTest {

	private MockMvc mvc;

	@RestController
	static class PingController {
		@GetMapping("/api/ping")
		String ping() {
			// Echo the logging context so the test can see the interceptor ran before the handler.
			return "user=" + MDC.get(RequestContextInterceptor.MDC_USER)
					+ " requestId=" + MDC.get(RequestContextInterceptor.MDC_REQUEST_ID);
		}
	}

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new PingController())
				.addInterceptors(new RequestContextInterceptor())
				.build();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void refusesWhenNobodyIsLoggedIn() throws Exception {
		mvc.perform(get("/api/ping")).andExpect(status().isUnauthorized());

		assertThat(MDC.get(RequestContextInterceptor.MDC_USER)).as("MDC cleaned up on refusal").isNull();
	}

	@Test
	void treatsSpringSecurityAnonymousUserAsNotLoggedIn() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
				"key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

		mvc.perform(get("/api/ping")).andExpect(status().isUnauthorized());
	}

	@Test
	void tagsTheRequestWithUserAndIdForALoggedInUser() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(
				new TestingAuthenticationToken("alice", "n/a", "ROLE_ELDER"));

		MvcResult result = mvc.perform(get("/api/ping")).andExpect(status().isOk()).andReturn();

		String requestId = result.getResponse().getHeader(RequestContextInterceptor.REQUEST_ID_HEADER);
		assertThat(requestId).as("a request id is minted and returned").hasSize(36);
		assertThat(result.getResponse().getContentAsString())
				.isEqualTo("user=alice requestId=" + requestId);
		assertThat(MDC.get(RequestContextInterceptor.MDC_USER)).as("MDC cleaned up afterwards").isNull();
		assertThat(MDC.get(RequestContextInterceptor.MDC_REQUEST_ID)).isNull();
	}

	@Test
	void keepsAClientSuppliedRequestId() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(
				new TestingAuthenticationToken("alice", "n/a", "ROLE_ELDER"));

		MvcResult result = mvc.perform(get("/api/ping")
						.header(RequestContextInterceptor.REQUEST_ID_HEADER, "retry-0042-abcd"))
				.andExpect(status().isOk()).andReturn();

		assertThat(result.getResponse().getHeader(RequestContextInterceptor.REQUEST_ID_HEADER))
				.isEqualTo("retry-0042-abcd");
	}

	@Test
	void replacesAClientSuppliedRequestIdThatCouldPolluteLogs() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(
				new TestingAuthenticationToken("alice", "n/a", "ROLE_ELDER"));

		MvcResult result = mvc.perform(get("/api/ping")
						.header(RequestContextInterceptor.REQUEST_ID_HEADER, "bad id\nwith newline"))
				.andExpect(status().isOk()).andReturn();

		assertThat(result.getResponse().getHeader(RequestContextInterceptor.REQUEST_ID_HEADER)).hasSize(36);
	}
}
