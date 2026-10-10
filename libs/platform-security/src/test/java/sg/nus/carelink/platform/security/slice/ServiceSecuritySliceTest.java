package sg.nus.carelink.platform.security.slice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import sg.nus.carelink.platform.security.ServiceSecurityAutoConfiguration;
import sg.nus.carelink.platform.security.SignedInUser;
import sg.nus.carelink.platform.security.SignedInUserSession;
import sg.nus.carelink.platform.security.SignedInUsers;

/**
 * How a service tests one controller on its own (docs/platform/building-a-service.md). A
 * {@code @WebMvcTest} slice does not load this library's auto-configuration by itself, so the test
 * imports it and runs with the chain the service has in production; the signed-in user is given as
 * core leaves it, a security context plus the two session attributes.
 */
@WebMvcTest
@ImportAutoConfiguration(ServiceSecurityAutoConfiguration.class)
class ServiceSecuritySliceTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void anAnonymousCallIsAnswered401() throws Exception {
		mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void aSignedInCallSeesWhoCoreSignedIn() throws Exception {
		mvc.perform(get("/api/me")
						.with(user("ana").roles("FAMILY"))
						.sessionAttr(SignedInUserSession.ID, 42L)
						.sessionAttr(SignedInUserSession.DISPLAY_NAME, "Ana Tan"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.username").value("ana"))
				.andExpect(jsonPath("$.displayName").value("Ana Tan"))
				.andExpect(jsonPath("$.roles[0]").value("FAMILY"))
				// Set by this library's chain only, not by Spring Boot's default one
				.andExpect(cookie().exists("XSRF-TOKEN"));
	}

	@Test
	void aWriteWithoutTheCsrfTokenIsRefused() throws Exception {
		mvc.perform(post("/api/things").with(user("ana").roles("FAMILY"))).andExpect(status().isForbidden());
	}

	@SpringBootConfiguration
	@Import(Endpoints.class)
	static class Service {
	}

	@RestController
	static class Endpoints {

		private final SignedInUsers users;

		Endpoints(SignedInUsers users) {
			this.users = users;
		}

		@GetMapping("/api/me")
		SignedInUser me() {
			return users.require();
		}

		@PostMapping("/api/things")
		void create() {
		}

	}

}
