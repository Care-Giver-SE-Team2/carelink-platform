package sg.nus.carelink.report.controller.open;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import sg.nus.carelink.events.EventHandler;
import sg.nus.carelink.events.EventMetadata;
import sg.nus.carelink.eventtypes.ProviderUpdated;
import sg.nus.carelink.platform.security.OpenPlatformTokens;
import sg.nus.carelink.report.application.ProviderJobService;
import sg.nus.carelink.report.application.StandaloneVisits;
import sg.nus.carelink.report.application.ValueAddedNotifier;
import sg.nus.carelink.report.application.ValueAddedVisitAssignment;
import sg.nus.carelink.report.application.VisitCover;
import sg.nus.carelink.report.application.VisitReassignment;
import sg.nus.carelink.report.support.SignedInSessions;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * The Open Platform's job flow in report, end to end against MySQL: a provider kept from
 * {@code ProviderUpdated}, a family's request offered to it when no caregiver is free, and the job
 * API with real tokens, signed by the test's key as notification signs them. The visit service is
 * stood in for; core is the core stand-in, answering from the rows laid out here.
 */
@SpringBootTest(properties = { "carelink.report.schedule-cron=-", "carelink.provider-jobs.expire-initial-delay=PT1H",
		"carelink.value-added.settle-initial-delay=PT1H" })
@AutoConfigureMockMvc
@Import(ProviderJobsIT.TestKeys.class)
class ProviderJobsIT {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 9, 0);
	private static final LocalDateTime START = LocalDateTime.of(2026, 10, 18, 10, 0);
	private static final String ISSUER = "carelink-open-platform";
	private static final KeyPair KEYS = keys();
	private static final MovableClock CLOCK = new MovableClock();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, ProviderJobsIT.class, "+08:00", "connectionTimeZone=Asia/Singapore");
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private List<EventHandler<?>> handlers;

	@Autowired
	private ProviderJobService jobs;

	@MockitoBean
	private ValueAddedVisitAssignment assignment;

	@MockitoBean
	private StandaloneVisits visits;

	@MockitoBean
	private VisitReassignment reassignment;

	@MockitoBean
	private VisitCover cover;

	/** The family's messages are the value-added tests'; core's family lookups are not stood in for here. */
	@MockitoBean
	private ValueAddedNotifier familyMessages;

	@TestConfiguration(proxyBeanMethods = false)
	static class TestKeys {

		/** notification's keys, as the test's: the same checks as the real decoder, on another key. */
		@Bean
		JwtDecoder openPlatformJwtDecoder() {
			NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
			decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER),
					OpenPlatformTokens.accessTokensOnly()));
			return decoder;
		}

		@Bean
		@Primary
		Clock providerJobsClock() {
			return CLOCK;
		}
	}

	@BeforeEach
	void layOut() {
		CLOCK.set(NOW);
		reset(assignment, visits, reassignment, cover);
		when(assignment.chooseCaregiver(anyLong(), any(), any())).thenReturn(Optional.empty());
		when(visits.schedule(any())).thenReturn(900L);
		// The visit approval books for a manager to staff, with nobody on it yet
		when(visits.find(anyLong())).thenReturn(Optional.of(new StandaloneVisits.State(900L, null, "SCHEDULED", false)));
		for (String table : List.of("report_provider_job_key", "report_provider_job", "report_provider_service",
				"report_provider", "value_added_service_request", "outbox_event", "elder_family_binding", "elder",
				"family_member", "user_role", "app_user")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("""
				INSERT INTO app_user (id, username, password_hash, display_name) VALUES
				(7, 'family-a', '{noop}x', 'Family A'), (10, 'manager', '{noop}x', 'Manager')
				""");
		jdbc.update("INSERT INTO user_role (user_id, role) VALUES (7, 'FAMILY'), (10, 'MANAGER')");
		jdbc.update("INSERT INTO family_member (id, user_id, full_name) VALUES (42, 7, 'Family A')");
		jdbc.update("""
				INSERT INTO elder (id, full_name, gender, date_of_birth, address, sector, preferred_dialects,
				                   mobility_level)
				VALUES (101, 'Lim Ah Kow', 'MALE', '1945-03-02', 'Blk 123 Hougang Ave 1, #05-67, Singapore 530123',
				        'NE', 'Hokkien,English', 'ASSISTIVE_CANE')
				""");
		jdbc.update("""
				INSERT INTO elder_family_binding (id, elder_id, family_member_id, relationship, access_scope, status)
				VALUES (701, 101, 42, 'DAUGHTER', 'FULL', 'ACTIVE')
				""");
		provider(509L, "cl_bright", "BrightHome Care", "ACTIVE", 1, "Hospital escort", "Companionship");
	}

	@Test
	void aRequestNobodyIsFreeForIsOfferedAcceptedAndCompleted() throws Exception {
		Long requestId = book();

		assertThat(requestStatus(requestId)).isEqualTo("DISPATCHED");
		assertThat(jdbc.queryForObject("select visit_id from value_added_service_request where id = ?", Long.class,
				requestId)).isEqualTo(900L);
		verify(visits).schedule(any());
		// MySQL keeps the payload as JSON, written back with a space after each colon
		Map<String, Object> event = lastEvent("ProviderJobUpdated");
		assertThat((String) event.get("payload")).contains("\"status\": \"NEW\"", "\"district\": \"19\"",
				"\"respondBy\": \"2026-10-17T09:00:00+08:00\"").doesNotContain("Lim", "Hougang Ave");
		String jobId = "JOB-" + jdbc.queryForObject("select id from report_provider_job", Long.class);

		mvc.perform(get("/api/open/v1/jobs").param("status", "NEW").header("Authorization", bearer("cl_bright")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].id").value(jobId))
				.andExpect(jsonPath("$.items[0].serviceName").value("Hospital escort"))
				.andExpect(jsonPath("$.items[0].scheduledStart").value("2026-10-18T10:00:00+08:00"))
				.andExpect(jsonPath("$.counts.NEW").value(1))
				.andExpect(jsonPath("$.counts.COMPLETED").value(0));
		mvc.perform(get("/api/open/v1/jobs/" + jobId).header("Authorization", bearer("cl_bright")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.elderSummary").value("Male, 81 · uses a walking aid"))
				.andExpect(jsonPath("$.languages").value("Hokkien, English"))
				.andExpect(jsonPath("$.bookedBy").value("Family member"))
				.andExpect(jsonPath("$.instructions").value("Ring twice"))
				.andExpect(jsonPath("$.address").doesNotExist());

		accept(jobId, null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
		accept(jobId, "key-1").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.respondBy").doesNotExist())
				.andExpect(jsonPath("$.address.text").value("Blk 123 Hougang Ave 1, #05-67, Singapore 530123"))
				.andExpect(jsonPath("$.timeline[1].step").value("ACCEPTED"));
		// The provider was first: the visit the manager had is called off
		verify(reassignment).callOff(org.mockito.ArgumentMatchers.eq(900L), any());
		// The same key again answers the job as it stands; for another action it is refused
		accept(jobId, "key-1").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
		mvc.perform(post("/api/open/v1/jobs/" + jobId + "/decline").header("Authorization", bearer("cl_bright"))
				.header("Idempotency-Key", "key-1").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"NO_PRACTITIONER\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

		complete(jobId, "key-2", "2026-10-16T08:00:00+08:00", "2026-10-16T10:00:00+08:00")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_TIMES"));
		complete(jobId, "key-3", "2026-10-16T05:00:00+08:00", "2026-10-16T08:00:00+08:00")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.completion.actualEnd").value("2026-10-16T08:00:00+08:00"));

		assertThat(requestStatus(requestId)).isEqualTo("COMPLETED");
		assertThat(jdbc.queryForObject("select count(*) from outbox_event where type = 'ProviderJobUpdated'",
				Integer.class)).isEqualTo(3);
	}

	@Test
	void anAcceptanceAfterTheFamilyCancelledIsRefusedWithTheReason() throws Exception {
		Long requestId = book();
		String jobId = onlyJobId();

		mvc.perform(post("/api/value-added-service-requests/" + requestId + "/cancellation")
				.session(sessions().of("manager")).with(csrf()))
				.andExpect(status().isOk());

		accept(jobId, "key-1").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("JOB_CANCELLED"))
				.andExpect(jsonPath("$.detail").value("The extra service was cancelled by a manager"));
		assertThat((String) lastEvent("ProviderJobUpdated").get("payload")).contains("\"status\": \"CANCELLED\"");
		assertThat(requestStatus(requestId)).isEqualTo("CANCELLED");
	}

	@Test
	void aDeclinedJobLeavesTheVisitWithTheManager() throws Exception {
		Long requestId = book();
		String jobId = onlyJobId();

		decline(jobId, "key-1", "{\"reason\":\"OTHER\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fields.note").exists());
		decline(jobId, "key-2", "{\"reason\":\"NO_PRACTITIONER\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DECLINED"))
				.andExpect(jsonPath("$.decline.reason").value("NO_PRACTITIONER"));

		verify(visits).schedule(any());
		verify(reassignment, never()).callOff(anyLong(), any());
		assertThat(requestStatus(requestId)).isEqualTo("DISPATCHED");
	}

	@Test
	void aManagerStaffingTheVisitFirstWithdrawsTheOffer() throws Exception {
		Long requestId = book();
		String jobId = onlyJobId();

		assign(requestId).andExpect(status().isOk());

		assertThat((String) lastEvent("ProviderJobUpdated").get("payload")).contains("\"status\": \"CANCELLED\"",
				"CareLink has staffed this job itself.");
		accept(jobId, "key-1").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("JOB_CANCELLED"));
		verify(reassignment, never()).callOff(anyLong(), any());
	}

	@Test
	void aProviderAcceptingFirstKeepsTheJobFromTheManager() throws Exception {
		Long requestId = book();
		accept(onlyJobId(), "key-1").andExpect(status().isOk());
		when(visits.find(anyLong())).thenReturn(Optional.of(new StandaloneVisits.State(900L, null, "CANCELLED", false)));

		assign(requestId).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("VALUE_ADDED_TAKEN_BY_PROVIDER"));

		verify(cover, never()).cover(anyLong(), anyLong(), anyLong());
		mvc.perform(get("/api/value-added-service-requests").session(sessions().of("manager")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].providerName").value("BrightHome Care"))
				.andExpect(jsonPath("$[0].providerJobStatus").value("ACCEPTED"))
				.andExpect(jsonPath("$[0].needsCaregiver").value(false));
	}

	@Test
	void aVisitStaffedSomeOtherWayWithdrawsTheOfferWhenTheProviderAccepts() throws Exception {
		book();
		String jobId = onlyJobId();
		when(visits.find(anyLong())).thenReturn(Optional.of(new StandaloneVisits.State(900L, 501L, "SCHEDULED", false)));

		accept(jobId, "key-1").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("JOB_CANCELLED"));

		assertThat(jdbc.queryForObject("select status from report_provider_job", String.class)).isEqualTo("CANCELLED");
		verify(reassignment, never()).callOff(anyLong(), any());
	}

	@Test
	void aJobNotAnsweredInTimeIsCalledOffEvenWhenTheAnswerComesLate() throws Exception {
		book();
		String jobId = onlyJobId();
		CLOCK.set(LocalDateTime.of(2026, 10, 17, 9, 0, 1));

		accept(jobId, "key-1").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RESPONSE_DEADLINE_PASSED"));

		// Kept, though the answer was refused: the job is CANCELLED, and the visit stays the manager's
		assertThat(jdbc.queryForObject("select status from report_provider_job", String.class)).isEqualTo("CANCELLED");
		verify(reassignment, never()).callOff(anyLong(), any());
		assertThat(jobs.unanswered()).isEmpty();
	}

	@Test
	void theSchedulerCallsOffAJobNobodyAnswered() throws Exception {
		book();
		CLOCK.set(LocalDateTime.of(2026, 10, 17, 9, 1));

		List<Long> due = jobs.unanswered();

		assertThat(due).hasSize(1);
		assertThat(jobs.expire(due.getFirst())).isTrue();
		assertThat(jobs.expire(due.getFirst())).isFalse();
		assertThat(jdbc.queryForObject("select status from report_provider_job", String.class)).isEqualTo("CANCELLED");
	}

	@Test
	void jobsGoToTheProviderOfferedOneLeastRecently() throws Exception {
		provider(510L, "cl_kind", "KindCare", "ACTIVE", 0, "Hospital escort");

		book();
		CLOCK.set(NOW.plusMinutes(1));
		book();

		assertThat(jdbc.queryForList("select provider_id from report_provider_job order by id", Long.class))
				.containsExactly(509L, 510L);
	}

	@Test
	void nobodyIsOfferedAJobTooNearItsStart() throws Exception {
		CLOCK.set(START.minusHours(4).minusMinutes(30));

		Long requestId = book();

		assertThat(jdbc.queryForObject("select visit_id from value_added_service_request where id = ?", Long.class,
				requestId)).isEqualTo(900L);
		assertThat(jdbc.queryForObject("select count(*) from report_provider_job", Integer.class)).isZero();
	}

	@Test
	void anotherProvidersJobARotatedClientAndASuspendedProviderAreRefused() throws Exception {
		provider(510L, "cl_kind", "KindCare", "ACTIVE", 0, "Companionship");
		book();
		String jobId = onlyJobId();

		mvc.perform(get("/api/open/v1/jobs/" + jobId).header("Authorization", bearer("cl_kind")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
		mvc.perform(post("/api/open/v1/jobs/" + jobId + "/accept").header("Authorization",
				bearer("cl_bright", OpenPlatformTokens.JOBS_READ)).header("Idempotency-Key", "k"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));

		provider(509L, "cl_bright_2", "BrightHome Care", "ACTIVE", 2, "Hospital escort");
		mvc.perform(get("/api/open/v1/jobs").header("Authorization", bearer("cl_bright")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

		provider(509L, "cl_bright_2", "BrightHome Care", "SUSPENDED", 3, "Hospital escort");
		mvc.perform(get("/api/open/v1/jobs").header("Authorization", bearer("cl_bright_2")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("PROVIDER_SUSPENDED"));

		// An older event, arriving late, changes nothing
		provider(509L, "cl_bright", "BrightHome Care", "ACTIVE", 1, "Hospital escort");
		assertThat(jdbc.queryForObject("select status from report_provider where provider_id = 509", String.class))
				.isEqualTo("SUSPENDED");
	}

	// ---------- helpers ----------

	/** The family books the escort for {@link #START} through the app's API; answers the request's id. */
	private Long book() throws Exception {
		String body = "{\"elderId\":101,\"valueAddedServiceId\":%d,\"requestedSchedule\":\"%s\",\"specialInstructions\":\"Ring twice\"}"
				.formatted(escortId(), START);
		String response = mvc.perform(post("/api/family/value-added-service-requests")
				.session(sessions().of("family-a")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().is2xxSuccessful())
				.andReturn().getResponse().getContentAsString();
		return Long.valueOf(response.replaceAll("(?s).*?\"id\"\\s*:\\s*(\\d+).*", "$1"));
	}

	/** A manager puts caregiver 501 on the request's visit, from the Extra services screen. */
	private ResultActions assign(Long requestId) throws Exception {
		return mvc.perform(post("/api/value-added-service-requests/" + requestId + "/caregiver")
				.session(sessions().of("manager")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"caregiverId\":501}"));
	}

	private SignedInSessions sessions() {
		return new SignedInSessions(jdbc);
	}

	private Long escortId() {
		return jdbc.queryForObject("select id from value_added_service where name = 'Hospital escort'", Long.class);
	}

	private String onlyJobId() {
		return "JOB-" + jdbc.queryForObject("select id from report_provider_job", Long.class);
	}

	private String requestStatus(Long requestId) {
		return jdbc.queryForObject("select status from value_added_service_request where id = ?", String.class,
				requestId);
	}

	private Map<String, Object> lastEvent(String type) {
		return jdbc.queryForMap("select type, payload from outbox_event where type = ? order by id desc limit 1",
				type);
	}

	@SuppressWarnings("unchecked")
	private void provider(Long id, String clientId, String name, String status, int version, String... services) {
		EventHandler<ProviderUpdated> handler = (EventHandler<ProviderUpdated>) handlers.stream()
				.filter(candidate -> ProviderUpdated.TYPE.equals(candidate.type()))
				.findFirst()
				.orElseThrow();
		handler.handle(new ProviderUpdated(id, clientId, name, status, List.of(services), version, null),
				new EventMetadata("evt-" + id + "-" + version, ProviderUpdated.TYPE, "notification", Instant.now(), null));
	}

	private ResultActions accept(String jobId, String key) throws Exception {
		var call = post("/api/open/v1/jobs/" + jobId + "/accept").header("Authorization", bearer("cl_bright"));
		return mvc.perform(key == null ? call : call.header("Idempotency-Key", key));
	}

	private ResultActions decline(String jobId, String key, String body) throws Exception {
		return mvc.perform(post("/api/open/v1/jobs/" + jobId + "/decline").header("Authorization", bearer("cl_bright"))
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions complete(String jobId, String key, String start, String end) throws Exception {
		return mvc.perform(post("/api/open/v1/jobs/" + jobId + "/complete").header("Authorization", bearer("cl_bright"))
				.header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
				.content("{\"actualStart\":\"%s\",\"actualEnd\":\"%s\",\"note\":\"Done\"}".formatted(start, end)));
	}

	private static String bearer(String clientId) throws Exception {
		return bearer(clientId, OpenPlatformTokens.JOBS_READ + " " + OpenPlatformTokens.JOBS_WRITE);
	}

	/** A provider's server token, as notification's token endpoint issues it. */
	private static String bearer(String clientId, String scope) throws Exception {
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISSUER).subject(clientId)
				.claim("client_id", clientId).claim("scope", scope).claim("token_use", "access")
				.issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600))).build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
		jwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
		return "Bearer " + jwt.serialize();
	}

	private static KeyPair keys() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	/** Singapore's clock, set by each test. */
	static final class MovableClock extends Clock {

		private volatile Instant now = Instant.EPOCH;

		void set(LocalDateTime wallClock) {
			now = wallClock.atZone(SINGAPORE).toInstant();
		}

		@Override
		public ZoneId getZone() {
			return SINGAPORE;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
