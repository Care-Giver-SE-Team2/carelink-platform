package sg.nus.carelink.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import sg.nus.carelink.testsupport.SharedMySql;

/**
 * Checks that the interceptor is wired onto the paths WebMvcConfig says, through
 * the real application: security filter chain first, interceptor second. The
 * behaviour of the interceptor itself is covered by RequestContextInterceptorTest.
 *
 * <p>Named *IT: runs under mvn verify -Pintegration in the pipeline; needs Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RequestContextWiringIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, RequestContextWiringIT.class, null);
	}

	@Autowired
	private MockMvc mvc;

	@Test
	void securityRejectsAnonymousApiCallsBeforeTheInterceptorRuns() throws Exception {
		MvcResult result = mvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andReturn();

		assertThat(result.getResponse().getHeader(RequestContextInterceptor.REQUEST_ID_HEADER))
				.as("the filter chain answered; the interceptor never ran")
				.isNull();
	}

	@Test
	void everyApiCallByALoggedInUserCarriesARequestId() throws Exception {
		// No such account exists, so the controller answers 404 — which is fine: the
		// point is that the interceptor ran before it and tagged the response.
		MvcResult result = mvc.perform(get("/api/auth/me").with(user("ghost").roles("ELDER")))
				.andExpect(status().isNotFound())
				.andReturn();

		assertThat(result.getResponse().getHeader(RequestContextInterceptor.REQUEST_ID_HEADER)).hasSize(36);
	}

	@Test
	void nonApiPathsAreLeftAlone() throws Exception {
		MvcResult result = mvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andReturn();

		assertThat(result.getResponse().getHeader(RequestContextInterceptor.REQUEST_ID_HEADER)).isNull();
	}
}
