package sg.nus.carelink.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A service that serves the Open Platform: {@code /api/open/**} takes the partners' bearer tokens, as
 * notification issues them, and the rest of the service keeps core's session rules. The tokens are
 * signed here with a key made for the test; the decoder trusts that key in place of notification's.
 */
@SpringBootTest(classes = OpenPlatformSecurityTest.Service.class, properties = {
		"carelink.open-platform.jwk-set-uri=http://keys.invalid/.well-known/jwks.json",
		"carelink.open-platform.issuer=https://issuer.test" })
@AutoConfigureMockMvc
class OpenPlatformSecurityTest {

	private static final String ISSUER = "https://issuer.test";

	private static final KeyPair KEYS = keys();

	private static final KeyPair OTHER_KEYS = keys();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ApplicationContext context;

	@Test
	void theOpenChainComesBeforeTheSessionChain() {
		assertThat(context.getBeanNamesForType(SecurityFilterChain.class))
				.containsExactly("openPlatformSecurityFilterChain", "serviceSecurityFilterChain");
	}

	@Test
	void anyoneMayApplyWithoutATokenOrCsrf() throws Exception {
		mvc.perform(post("/api/open/v1/applications")).andExpect(status().isOk());
	}

	@Test
	void gettingATokenAndTheKeysIsOpen() throws Exception {
		mvc.perform(post("/api/open/v1/portal/sessions")).andExpect(status().isOk());
		mvc.perform(post("/api/open/v1/oauth2/token")).andExpect(status().isOk());
		mvc.perform(get("/api/open/v1/.well-known/jwks.json")).andExpect(status().isOk());
	}

	@Test
	void aMissingTokenIsAnswered401WithTheContractsCode() throws Exception {
		mvc.perform(get("/api/open/v1/portal/application"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void aPortalUserReachesThePortalAndIsNamedByTheirToken() throws Exception {
		mvc.perform(get("/api/open/v1/portal/application").header("Authorization", bearer(portalUser())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.subject").value("user-sub-1"))
				.andExpect(jsonPath("$.username").value("ops@brighthome.sg"))
				.andExpect(jsonPath("$.portalUser").value(true));
	}

	@Test
	void aPortalUserMayNotCallTheJobApi() throws Exception {
		mvc.perform(get("/api/open/v1/jobs").header("Authorization", bearer(portalUser())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));
	}

	@Test
	void aProviderServerMayNotUseThePortal() throws Exception {
		mvc.perform(get("/api/open/v1/portal/application")
						.header("Authorization", bearer(client(OpenPlatformTokens.JOBS_READ))))
				.andExpect(status().isForbidden());
	}

	@Test
	void readingJobsNeedsTheReadScopeAndActingOnThemTheWriteScope() throws Exception {
		String readOnly = bearer(client(OpenPlatformTokens.JOBS_READ));
		mvc.perform(get("/api/open/v1/jobs").header("Authorization", readOnly))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientId").value("cl_prov_1"))
				.andExpect(jsonPath("$.portalUser").value(false));
		mvc.perform(post("/api/open/v1/jobs/JOB-1/accept").header("Authorization", readOnly))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));

		String both = bearer(client(OpenPlatformTokens.JOBS_READ + " " + OpenPlatformTokens.JOBS_WRITE));
		mvc.perform(post("/api/open/v1/jobs/JOB-1/accept").header("Authorization", both)).andExpect(status().isOk());
	}

	@Test
	void anExpiredTokenIsRefused() throws Exception {
		JWTClaimsSet expired = new JWTClaimsSet.Builder(client(OpenPlatformTokens.JOBS_READ))
				.issueTime(Date.from(Instant.now().minusSeconds(7200)))
				.expirationTime(Date.from(Instant.now().minusSeconds(3600)))
				.build();
		mvc.perform(get("/api/open/v1/jobs").header("Authorization", bearer(expired)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void aTokenSignedWithAnotherKeyIsRefused() throws Exception {
		mvc.perform(get("/api/open/v1/jobs").header("Authorization", bearer(client(OpenPlatformTokens.JOBS_READ), OTHER_KEYS)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenFromAnotherIssuerIsRefused() throws Exception {
		JWTClaimsSet elsewhere = new JWTClaimsSet.Builder(client(OpenPlatformTokens.JOBS_READ))
				.issuer("https://elsewhere.test").build();
		mvc.perform(get("/api/open/v1/jobs").header("Authorization", bearer(elsewhere)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anIdTokenIsRefused() throws Exception {
		JWTClaimsSet idToken = new JWTClaimsSet.Builder(portalUser()).claim("token_use", "id").build();
		mvc.perform(get("/api/open/v1/portal/application").header("Authorization", bearer(idToken)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anUnknownOpenPathIsRefusedWhateverTheToken() throws Exception {
		mvc.perform(get("/api/open/v1/other").header("Authorization", bearer(portalUser())))
				.andExpect(status().isForbidden());
	}

	@Test
	void theAppsOwnPathsKeepTheSession() throws Exception {
		mvc.perform(get("/api/me").header("Authorization", bearer(portalUser()))).andExpect(status().isUnauthorized());
	}

	private static JWTClaimsSet portalUser() {
		return claims().subject("user-sub-1").claim("username", "ops@brighthome.sg")
				.claim("client_id", "portal-client").build();
	}

	private static JWTClaimsSet client(String scope) {
		return claims().subject("cl_prov_1").claim("client_id", "cl_prov_1").claim("scope", scope).build();
	}

	private static JWTClaimsSet.Builder claims() {
		Instant now = Instant.now();
		return new JWTClaimsSet.Builder().issuer(ISSUER).claim("token_use", "access")
				.issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600)));
	}

	private static String bearer(JWTClaimsSet claims) throws JOSEException {
		return bearer(claims, KEYS);
	}

	private static String bearer(JWTClaimsSet claims, KeyPair keys) throws JOSEException {
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
		jwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
		return "Bearer " + jwt.serialize();
	}

	private static KeyPair keys() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (java.security.NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(Endpoints.class)
	static class Service {

		/** notification's keys, as the test's: the same checks as the real decoder, on another key. */
		@Bean
		JwtDecoder openPlatformJwtDecoder() {
			NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
			decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER),
					OpenPlatformTokens.accessTokensOnly()));
			return decoder;
		}
	}

	@RestController
	static class Endpoints {

		private final OpenPlatformCallers callers;
		private final SignedInUsers users;

		Endpoints(OpenPlatformCallers callers, SignedInUsers users) {
			this.callers = callers;
			this.users = users;
		}

		@PostMapping("/api/open/v1/applications")
		void apply() {
		}

		@PostMapping("/api/open/v1/portal/sessions")
		void signIn() {
		}

		@PostMapping("/api/open/v1/oauth2/token")
		void token() {
		}

		@GetMapping("/api/open/v1/.well-known/jwks.json")
		Map<String, Object> keys() {
			return Map.of("keys", new Object[0]);
		}

		@GetMapping("/api/open/v1/portal/application")
		Map<String, Object> application() {
			OpenPlatformCaller caller = callers.requirePortalUser();
			return Map.of("subject", caller.subject(), "username", caller.username(), "portalUser", caller.portalUser());
		}

		@GetMapping("/api/open/v1/jobs")
		Map<String, Object> jobs() {
			OpenPlatformCaller caller = callers.requireClient();
			return Map.of("clientId", caller.clientId(), "portalUser", caller.portalUser());
		}

		@PostMapping("/api/open/v1/jobs/{jobId}/accept")
		void accept() {
		}

		@GetMapping("/api/open/v1/other")
		void other() {
		}

		@GetMapping("/api/me")
		SignedInUser me() {
			return users.require();
		}
	}
}
