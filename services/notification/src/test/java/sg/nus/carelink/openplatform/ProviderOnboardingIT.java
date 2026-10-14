package sg.nus.carelink.openplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import sg.nus.carelink.platform.security.SignedInUserSession;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * The Open Platform's onboarding end to end, against notification's own database and through the real
 * security chains, with notification issuing the tokens: a provider applies, signs in to the portal, is approved by a manager, collects its credentials once, gets a server token with them, and
 * manages its webhooks. report hears of the provider through the outbox. Every call is the one the
 * Partner Portal or a provider's server makes, with the contract's codes on every refusal.
 */
@SpringBootTest(properties = "carelink.webhooks.scheduler-enabled=false")
@AutoConfigureMockMvc
class ProviderOnboardingIT {

	private static final String PASSWORD = "correct horse battery";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, ProviderOnboardingIT.class, null, "connectionTimeZone=Asia/Singapore");
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void aProviderAppliesIsApprovedCollectsItsCredentialsOnceAndGetsAServerToken() throws Exception {
		String email = "ops@brighthome-it.sg";
		String reference = apply(email);
		assertThat(reference).matches("APP-\\d{4}-\\d{5}");
		mvc.perform(post("/api/open/v1/applications").contentType(MediaType.APPLICATION_JSON)
						.content(application(email.toUpperCase())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS"));

		// The portal before approval: the application, and nothing of a provider's
		String portal = signIn(email, PASSWORD);
		mvc.perform(get("/api/open/v1/portal/application").header("Authorization", portal))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(reference))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.contactEmail").value(email))
				.andExpect(jsonPath("$.submittedAt").value(org.hamcrest.Matchers.endsWith("+08:00")))
				.andExpect(jsonPath("$.suspended").value(false));
		mvc.perform(get("/api/open/v1/portal/credentials").header("Authorization", portal))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_APPROVED"));
		mvc.perform(post("/api/open/v1/portal/credentials/collect").header("Authorization", portal))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_APPROVED"));

		// A manager approves it, in the CareLink app
		long applicationId = pendingId(email);
		mvc.perform(post("/api/provider-applications/" + applicationId + "/approve").with(caregiver()).with(csrf()))
				.andExpect(status().isForbidden());
		String approved = mvc.perform(post("/api/provider-applications/" + applicationId + "/approve")
						.with(manager()).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value((int) applicationId))
				.andExpect(jsonPath("$.reference").value(reference))
				.andExpect(jsonPath("$.contactName").value("Mei Ling Tan"))
				.andExpect(jsonPath("$.webhookUrl").value("https://hooks.example-provider.sg/carelink"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.credentialsCollected").value(false))
				.andReturn().getResponse().getContentAsString();
		String clientId = JsonPath.read(approved, "$.clientId");
		assertThat(approved).as("the manager never sees a secret").doesNotContain("secret", "whsec_");
		mvc.perform(post("/api/provider-applications/" + applicationId + "/approve").with(manager()).with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_DECIDED"));

		List<Map<String, Object>> told = jdbc.queryForList(
				"SELECT payload FROM outbox_event WHERE type = 'ProviderUpdated' AND JSON_EXTRACT(payload, '$.providerId') = ?",
				applicationId);
		assertThat(told).hasSize(1);
		assertThat(JsonPath.<String>read(told.getFirst().get("payload").toString(), "$.clientId")).isEqualTo(clientId);

		// The credentials, once
		String collected = mvc.perform(post("/api/open/v1/portal/credentials/collect").header("Authorization", portal))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientId").value(clientId))
				.andReturn().getResponse().getContentAsString();
		String clientSecret = JsonPath.read(collected, "$.clientSecret");
		String webhookSecret = JsonPath.read(collected, "$.webhookSecret");
		assertThat(webhookSecret).startsWith("whsec_");
		mvc.perform(post("/api/open/v1/portal/credentials/collect").header("Authorization", portal))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.code").value("CREDENTIALS_COLLECTED"));
		assertNoSecretKept(applicationId, clientSecret, webhookSecret);

		mvc.perform(get("/api/open/v1/portal/credentials").header("Authorization", portal))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.secretHint").value(clientSecret.substring(clientSecret.length() - 4)))
				.andExpect(jsonPath("$.tokenUrl").value("/api/open/v1/oauth2/token"))
				.andExpect(jsonPath("$.scopes[0]").value("open-platform/jobs.read"));

		// The provider's server: a token from the client credentials, which the portal does not take
		String server = clientToken(clientId, clientSecret);
		mvc.perform(get("/api/open/v1/portal/application").header("Authorization", server))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));
		mvc.perform(post("/api/open/v1/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.header("Authorization", basic(clientId, "not-the-secret"))
						.param("grant_type", "client_credentials"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_client"));

		// Rotating the client secret: a new client, the old secret refused, report told
		String rotated = mvc.perform(post("/api/open/v1/portal/credentials/rotate").header("Authorization", portal))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String newClientId = JsonPath.read(rotated, "$.clientId");
		assertThat(newClientId).isNotEqualTo(clientId);
		clientToken(newClientId, JsonPath.read(rotated, "$.clientSecret"));
		mvc.perform(post("/api/open/v1/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.header("Authorization", basic(clientId, clientSecret))
						.param("grant_type", "client_credentials"))
				.andExpect(status().isUnauthorized());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE type = 'ProviderUpdated' "
				+ "AND JSON_EXTRACT(payload, '$.providerId') = ?", Integer.class, applicationId)).isEqualTo(2);
	}

	@Test
	void anApprovedProviderManagesItsWebhooks() throws Exception {
		String email = "ops@webhooks-it.sg";
		apply(email);
		long providerId = pendingId(email);
		mvc.perform(post("/api/provider-applications/" + providerId + "/approve").with(manager()).with(csrf()))
				.andExpect(status().isOk());
		String portal = signIn(email, PASSWORD);
		mvc.perform(post("/api/open/v1/portal/credentials/collect").header("Authorization", portal))
				.andExpect(status().isOk());

		mvc.perform(get("/api/open/v1/portal/webhook").header("Authorization", portal))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("https://hooks.example-provider.sg/carelink"))
				.andExpect(jsonPath("$.events[0]").value("job.cancelled"))
				.andExpect(jsonPath("$.events[1]").value("job.dispatched"));
		mvc.perform(put("/api/open/v1/portal/webhook").header("Authorization", portal)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"url\":\"http://plain.example.sg/hook\",\"events\":[\"job.dispatched\"]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fields.url").value("Must be HTTPS."));
		mvc.perform(put("/api/open/v1/portal/webhook").header("Authorization", portal)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"url\":\"https://new.example-provider.sg/hook\",\"events\":[\"ping\"]}"))
				.andExpect(status().isBadRequest());
		mvc.perform(put("/api/open/v1/portal/webhook").header("Authorization", portal)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"url\":\"https://new.example-provider.sg/hook\",\"events\":[\"job.dispatched\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("https://new.example-provider.sg/hook"))
				.andExpect(jsonPath("$.events.length()").value(1));

		String ping = mvc.perform(post("/api/open/v1/portal/webhook/test").header("Authorization", portal))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.event").value("ping"))
				.andExpect(jsonPath("$.status").value("RETRYING"))
				.andExpect(jsonPath("$.request.url").value("https://new.example-provider.sg/hook"))
				.andExpect(jsonPath("$.request.headers['X-CareLink-Signature']").value(org.hamcrest.Matchers.startsWith("v1=")))
				.andReturn().getResponse().getContentAsString();
		String deliveryId = JsonPath.read(ping, "$.id");

		mvc.perform(get("/api/open/v1/portal/webhook/deliveries").header("Authorization", portal).param("status", "RETRYING"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(deliveryId))
				.andExpect(jsonPath("$.totalElements").value(1));
		mvc.perform(get("/api/open/v1/portal/webhook/deliveries").header("Authorization", portal).param("status", "LOST"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.status").exists());
		mvc.perform(get("/api/open/v1/portal/webhook/deliveries").header("Authorization", portal).param("size", "500"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mvc.perform(get("/api/open/v1/portal/webhook/deliveries/" + deliveryId).header("Authorization", portal))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.request.body").value(org.hamcrest.Matchers.containsString("\"type\":\"ping\"")));
		mvc.perform(get("/api/open/v1/portal/webhook/deliveries/dlv_nobody").header("Authorization", portal))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("DELIVERY_NOT_FOUND"));
		mvc.perform(post("/api/open/v1/portal/webhook/deliveries/" + deliveryId + "/resend").header("Authorization", portal))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DELIVERY_NOT_FAILED"));

		String rotated = mvc.perform(post("/api/open/v1/portal/webhook/secret/rotate").header("Authorization", portal))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String secret = JsonPath.read(rotated, "$.webhookSecret");
		mvc.perform(get("/api/open/v1/portal/webhook").header("Authorization", portal))
				.andExpect(jsonPath("$.secretHint").value(secret.substring(secret.length() - 4)));

		mvc.perform(get("/api/open/v1/portal/health").header("Authorization", portal))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.retrying").value(1))
				.andExpect(jsonPath("$.suspended").value(false));
	}

	@Test
	void aManagerRejectsSuspendsAndReinstatesAndTheProviderSeesEach() throws Exception {
		String rejectedEmail = "ops@rejected-it.sg";
		apply(rejectedEmail);
		long rejectedId = pendingId(rejectedEmail);
		mvc.perform(post("/api/provider-applications/" + rejectedId + "/reject").with(manager()).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/provider-applications/" + rejectedId + "/reject").with(manager()).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Missing insurance certificate\"}"))
				.andExpect(status().isOk());
		mvc.perform(get("/api/open/v1/portal/application").header("Authorization", signIn(rejectedEmail, PASSWORD)))
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.reason").value("Missing insurance certificate"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE type = 'ProviderUpdated' "
				+ "AND JSON_EXTRACT(payload, '$.providerId') = ?", Integer.class, rejectedId)).isZero();

		String email = "ops@suspended-it.sg";
		apply(email);
		long providerId = pendingId(email);
		mvc.perform(post("/api/provider-applications/" + providerId + "/approve").with(manager()).with(csrf()))
				.andExpect(status().isOk());
		mvc.perform(get("/api/providers").with(manager()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + providerId + ")].health.delivered").value(0));
		mvc.perform(post("/api/providers/" + providerId + "/suspend").with(manager()).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUSPENDED"));
		mvc.perform(post("/api/providers/" + providerId + "/suspend").with(manager()).with(csrf()))
				.andExpect(status().isConflict());
		String portal = signIn(email, PASSWORD);
		mvc.perform(get("/api/open/v1/portal/application").header("Authorization", portal))
				.andExpect(jsonPath("$.suspended").value(true));
		mvc.perform(post("/api/providers/" + providerId + "/reinstate").with(manager()).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));
		assertThat(jdbc.queryForObject("SELECT enabled FROM provider_webhook_endpoint WHERE provider_id = ?",
				Boolean.class, providerId)).isTrue();
		assertThat(jdbc.queryForList("SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.status')) FROM outbox_event "
				+ "WHERE type = 'ProviderUpdated' AND JSON_EXTRACT(payload, '$.providerId') = ? ORDER BY id", String.class,
				providerId)).containsExactly("ACTIVE", "SUSPENDED", "ACTIVE");
	}

	@Test
	void aBadApplicationIsAnsweredWithEachFieldsProblem() throws Exception {
		mvc.perform(post("/api/open/v1/applications").contentType(MediaType.APPLICATION_JSON).content("""
						{"organisationName":"","contactName":"Mei","contactEmail":"not-an-email","contactPhone":"+6591234567",
						 "services":[],"webhookUrl":"https://hooks.example.sg","password":"short","agreedToTerms":false}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fields.organisationName").exists())
				.andExpect(jsonPath("$.fields.contactEmail").exists())
				.andExpect(jsonPath("$.fields.services").exists())
				.andExpect(jsonPath("$.fields.password").exists())
				.andExpect(jsonPath("$.fields.agreedToTerms").exists());
		mvc.perform(post("/api/open/v1/applications").contentType(MediaType.APPLICATION_JSON)
						.content(application("ops@plainhttp-it.sg").replace("https://", "http://")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.webhookUrl").value("Must be HTTPS."));
	}

	@Test
	void theSignInTakesOnlyTheRightPasswordAndTheKeysArePublished() throws Exception {
		apply("ops@signin-it.sg");
		signInAs("ops@signin-it.sg", "wrong password")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		signInAs("nobody@signin-it.sg", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		signIn(" OPS@signin-it.sg ", PASSWORD);
		mvc.perform(get("/api/open/v1/portal/application").header("Authorization", "Bearer not.a.token"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		mvc.perform(get("/api/open/v1/.well-known/jwks.json"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keys[0].kty").value("RSA"))
				.andExpect(jsonPath("$.keys[0].d").doesNotExist());
		mvc.perform(post("/api/open/v1/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("grant_type", "password"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("unsupported_grant_type"));
	}

	@Test
	void fiveWrongPasswordsInARowLockTheSignInForAQuarterOfAnHour() throws Exception {
		String email = "ops@locked-it.sg";
		apply(email);
		for (int attempt = 1; attempt <= 4; attempt++) {
			signInAs(email, "wrong password").andExpect(status().isUnauthorized());
		}
		signIn(email, PASSWORD);
		for (int attempt = 1; attempt <= 5; attempt++) {
			signInAs(email, "wrong password").andExpect(status().isUnauthorized());
		}
		signInAs(email, PASSWORD)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));

		jdbc.update("UPDATE provider_portal_user SET locked_until = '2000-01-01' WHERE username = ?", email);
		signIn(email, PASSWORD);
	}

	@Test
	void theTokenKeyIsKeptEncryptedSoEveryReplicaAndRestartSignsWithIt() {
		Map<String, Object> key = jdbc.queryForMap("SELECT key_id, private_key FROM provider_token_key");
		assertThat(key.get("private_key").toString()).startsWith("v1:").doesNotContain("\"d\"");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_token_key", Integer.class)).isOne();
	}

	private String apply(String email) throws Exception {
		String answer = mvc.perform(post("/api/open/v1/applications").contentType(MediaType.APPLICATION_JSON)
						.content(application(email)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(answer, "$.applicationId");
	}

	private static String application(String email) {
		return """
				{"organisationName":"BrightHome Care","contactName":"Mei Ling Tan","contactEmail":"%s",
				 "contactPhone":"+6591234567","services":["Hospital escort","Companionship"],
				 "webhookUrl":"https://hooks.example-provider.sg/carelink","password":"%s","agreedToTerms":true}"""
				.formatted(email, PASSWORD);
	}

	private long pendingId(String email) throws Exception {
		String pending = mvc.perform(get("/api/provider-applications").with(manager()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<Integer> ids = JsonPath.read(pending, "$[?(@.contactEmail == '" + email + "')].id");
		assertThat(ids).hasSize(1);
		return ids.getFirst();
	}

	/** Signs in as the Partner Portal does. */
	private String signIn(String email, String password) throws Exception {
		String answer = signInAs(email, password).andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600))
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(answer, "$.accessToken");
	}

	private ResultActions signInAs(String email, String password) throws Exception {
		return mvc.perform(post("/api/open/v1/portal/sessions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
	}

	private String clientToken(String clientId, String clientSecret) throws Exception {
		String answer = mvc.perform(post("/api/open/v1/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.header("Authorization", basic(clientId, clientSecret))
						.param("grant_type", "client_credentials"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token_type").value("Bearer"))
				.andExpect(jsonPath("$.expires_in").value(3600))
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(answer, "$.access_token");
	}

	private void assertNoSecretKept(long providerId, String clientSecret, String webhookSecret) {
		Map<String, Object> provider = jdbc.queryForMap("SELECT * FROM provider WHERE id = ?", providerId);
		Map<String, Object> endpoint = jdbc.queryForMap("SELECT * FROM provider_webhook_endpoint WHERE provider_id = ?",
				providerId);
		assertThat(provider.get("uncollected_client_secret")).isNull();
		assertThat(provider.get("uncollected_webhook_secret")).isNull();
		assertThat(provider.toString() + endpoint).doesNotContain(clientSecret).doesNotContain(webhookSecret);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_client WHERE secret_hash = ?", Integer.class,
				clientSecret)).as("a hash is kept, not the secret").isZero();
	}

	private static String basic(String id, String secret) {
		return "Basic " + Base64.getEncoder().encodeToString((id + ":" + secret).getBytes(StandardCharsets.UTF_8));
	}

	private static RequestPostProcessor manager() {
		return request -> {
			request.getSession(true).setAttribute(SignedInUserSession.ID, 42L);
			return user("it-provider-manager").roles("MANAGER").postProcessRequest(request);
		};
	}

	private static RequestPostProcessor caregiver() {
		return user("it-provider-caregiver").roles("CAREGIVER");
	}
}
