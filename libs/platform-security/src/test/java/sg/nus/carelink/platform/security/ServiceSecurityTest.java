package sg.nus.carelink.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A minimal service that only depends on this library gets core's security rules: anonymous calls
 * are refused with 401, health stays open, the signed-in user comes from the shared session, writes
 * need the CSRF token, and the service itself never starts a session.
 */
@SpringBootTest(classes = ServiceSecurityTest.Service.class)
@AutoConfigureMockMvc
class ServiceSecurityTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void anAnonymousCallIsRefusedWithoutStartingASession() throws Exception {
		MvcResult result = mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andReturn();

		jakarta.servlet.http.HttpSession session = result.getRequest().getSession(false);
		assertThat(session)
				.as("session holding %s", session == null ? "-" : java.util.Collections.list(session.getAttributeNames()))
				.isNull();
	}

	@Test
	void healthStaysOpen() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void theSignedInUserComesFromTheSessionCoreWrote() throws Exception {
		mvc.perform(get("/api/me").session(sessionOf("ana", 42L, "Ana Tan", "ROLE_FAMILY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.username").value("ana"))
				.andExpect(jsonPath("$.displayName").value("Ana Tan"))
				.andExpect(jsonPath("$.roles[0]").value("FAMILY"));
	}

	@Test
	void aWriteNeedsTheCsrfToken() throws Exception {
		MockHttpSession session = sessionOf("ana", 42L, "Ana Tan", "ROLE_FAMILY");

		mvc.perform(post("/api/things").session(session)).andExpect(status().isForbidden());
		mvc.perform(post("/api/things").session(session).with(csrf().asHeader())).andExpect(status().isOk());
	}

	/** A session as core leaves it after sign-in: the security context plus the two user attributes. */
	private static MockHttpSession sessionOf(String username, long id, String displayName, String... authorities) {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
				new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
						username, null, List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList())));
		SignedInUserSession.remember(session, id, displayName);
		return session;
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
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
