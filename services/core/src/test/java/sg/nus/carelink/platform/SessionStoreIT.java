package sg.nus.carelink.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationContext;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;
import sg.nus.carelink.platform.security.ConcurrentRedisSessionRepository;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import sg.nus.carelink.platform.security.SignedInUserSession;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.testsupport.SharedRedis;

/**
 * A login is kept in Redis, not in the memory of the replica that handled it, so the next request
 * can land on any replica. The test carries nothing but the cookies between requests, the way a
 * browser behind the load balancer does.
 */
@SpringBootTest(properties = {
		// Turn the Redis session store back on; the other tests switch it off (config/application.properties)
		"spring.autoconfigure.exclude=",
		"carelink.report.schedule-cron=-",
		"carelink.escalation.scan-initial-delay=PT1H"})
@AutoConfigureMockMvc
class SessionStoreIT {

	private static final String SESSION_KEYS = "carelink:session:sessions:";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private ConcurrentRedisSessionRepository sessions;

	@Autowired
	private RedisSessionRepository defaults;

	@Autowired
	private ApplicationContext context;

	@DynamicPropertySource
	static void stores(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, SessionStoreIT.class, "+08:00", "connectionTimeZone=Asia/Singapore");
		SharedRedis.register(registry);
	}

	@BeforeEach
	void manager() {
		jdbc.update("DELETE FROM user_role WHERE user_id = 901");
		jdbc.update("DELETE FROM app_user WHERE id = 901");
		jdbc.update("""
				INSERT INTO app_user (id, username, password_hash, display_name)
				VALUES (901, 'session-check', '{noop}test-password', 'Session Check')
				""");
		jdbc.update("INSERT INTO user_role (user_id, role) VALUES (901, 'MANAGER')");
	}

	@Test
	void theLoginIsStoredInRedisAndTheCookieAloneCarriesIt() throws Exception {
		Cookie csrf = csrf();
		Cookie session = login(csrf);

		String key = SESSION_KEYS + sessionId(session);
		assertThat(redis.hasKey(key)).isTrue();
		// The account id and display name the other services read (SignedInUsers)
		assertThat(redis.opsForHash().hasKey(key, "sessionAttr:" + SignedInUserSession.ID)).isTrue();
		assertThat(redis.opsForHash().hasKey(key, "sessionAttr:" + SignedInUserSession.DISPLAY_NAME)).isTrue();
		mvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("session-check"));
	}

	@Test
	void signingOutRemovesTheSessionFromRedis() throws Exception {
		Cookie csrf = csrf();
		Cookie session = login(csrf);

		mvc.perform(post("/api/auth/logout").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
				.andExpect(status().is2xxSuccessful());

		assertThat(redis.hasKey(SESSION_KEYS + sessionId(session))).isFalse();
		mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void httpUsesTheAtomicRepositoryAndKeepsTheDefaultRedisFormat() {
		assertThat(context.getBean(SessionRepository.class)).isSameAs(sessions);
		var session = sessions.createSession();
		session.setAttribute("example", "shared");
		sessions.save(session);
		Session legacyReader = defaults.findById(session.getId());
		assertThat(legacyReader).isNotNull();
		assertThat(legacyReader.<String>getAttribute("example")).isEqualTo("shared");
		assertThat(redis.getExpire(SESSION_KEYS + session.getId())).isPositive();
	}

	@Test
	void anExistingLegacyHashCanBeReadAndUpdatedWithoutLosingOtherAttributes() {
		String id = java.util.UUID.randomUUID().toString();
		long now = Instant.now().toEpochMilli();
		defaults.getSessionRedisOperations().opsForHash().putAll(SESSION_KEYS + id, Map.of(
				"creationTime", now, "lastAccessedTime", now, "maxInactiveInterval", 1800,
				"sessionAttr:legacy", "retained"));
		var session = sessions.findById(id);
		session.setAttribute("newAttribute", "added");
		sessions.save(session);
		Session legacyReader = defaults.findById(id);
		assertThat(legacyReader.<String>getAttribute("legacy")).isEqualTo("retained");
		assertThat(legacyReader.<String>getAttribute("newAttribute")).isEqualTo("added");
	}

	@Test
	void aLogoutWinsOverBothAlreadyLoadedRequestSnapshots() {
		var session = sessions.createSession();
		sessions.save(session);
		var first = sessions.findById(session.getId());
		var second = sessions.findById(session.getId());
		first.setLastAccessedTime(Instant.now());
		second.setAttribute("late", "must not resurrect authentication");
		sessions.deleteById(session.getId());
		sessions.save(first);
		sessions.save(second);
		assertThat(redis.hasKey(SESSION_KEYS + session.getId())).isFalse();
		assertThat(sessions.findById(session.getId())).isNull();
	}

	@Test
	void concurrentDeltasPreserveEachOthersUnrelatedAttributes() throws Exception {
		var initial = sessions.createSession();
		initial.setAttribute("remove", "old");
		sessions.save(initial);
		var first = sessions.findById(initial.getId());
		var second = sessions.findById(initial.getId());
		first.setAttribute("first", "one");
		first.removeAttribute("remove");
		second.setAttribute("second", "two");
		CountDownLatch start = new CountDownLatch(1);
		try (var pool = Executors.newFixedThreadPool(2)) {
			var a = pool.submit(() -> { start.await(); sessions.save(first); return true; });
			var b = pool.submit(() -> { start.await(); sessions.save(second); return true; });
			start.countDown();
			a.get(5, TimeUnit.SECONDS);
			b.get(5, TimeUnit.SECONDS);
		}
		var finalSession = sessions.findById(initial.getId());
		assertThat(finalSession.<String>getAttribute("first")).isEqualTo("one");
		assertThat(finalSession.<String>getAttribute("second")).isEqualTo("two");
		assertThat(finalSession.<String>getAttribute("remove")).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"creationTime", "lastAccessedTime", "maxInactiveInterval"})
	void incompleteHashesFailClosedAndAreRemoved(String missingField) throws Exception {
		Cookie csrf = csrf();
		Cookie session = login(csrf);
		String key = SESSION_KEYS + sessionId(session);
		redis.opsForHash().delete(key, missingField);
		mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
		assertThat(redis.hasKey(key)).isFalse();
		mvc.perform(post("/api/auth/logout").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
				.andExpect(status().isNoContent());
	}

	@Test
	void logoutItselfCanEncounterAnIncompleteHashWithoutReturning500() throws Exception {
		Cookie csrf = csrf();
		Cookie session = login(csrf);
		String key = SESSION_KEYS + sessionId(session);
		redis.opsForHash().delete(key, "creationTime");
		mvc.perform(post("/api/auth/logout").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
				.andExpect(status().isNoContent());
		assertThat(redis.hasKey(key)).isFalse();
	}

	@Test
	void aLoadedSnapshotCannotRepairAndResurrectAnIncompleteHash() {
		var session = sessions.createSession();
		sessions.save(session);
		var pending = sessions.findById(session.getId());
		pending.setLastAccessedTime(Instant.now());
		redis.opsForHash().delete(SESSION_KEYS + session.getId(), "creationTime");
		sessions.save(pending);
		assertThat(redis.hasKey(SESSION_KEYS + session.getId())).isFalse();
	}

	@Test
	void sessionIdRotationIsAtomicAndCannotReviveALoggedOutSnapshot() {
		var session = sessions.createSession();
		session.setAttribute("kept", "value");
		sessions.save(session);
		String oldId = session.getId();
		var rotation = sessions.findById(oldId);
		String newId = rotation.changeSessionId();
		sessions.save(rotation);
		assertThat(redis.hasKey(SESSION_KEYS + oldId)).isFalse();
		assertThat(sessions.findById(newId).<String>getAttribute("kept")).isEqualTo("value");
		var late = sessions.findById(newId);
		String unpersistedId = late.changeSessionId();
		sessions.deleteById(newId);
		sessions.save(late);
		assertThat(late.getId()).isEqualTo(newId);
		assertThat(redis.hasKey(SESSION_KEYS + newId)).isFalse();
		assertThat(redis.hasKey(SESSION_KEYS + unpersistedId)).isFalse();
	}

	@Test
	void naturalExpiryCannotBeUndoneByAPendingRequest() throws Exception {
		var session = sessions.createSession();
		session.setMaxInactiveInterval(Duration.ofSeconds(1));
		sessions.save(session);
		var pending = sessions.findById(session.getId());
		long stop = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
		while (Boolean.TRUE.equals(redis.hasKey(SESSION_KEYS + session.getId())) && System.nanoTime() < stop) {
			Thread.sleep(25);
		}
		assertThat(redis.hasKey(SESSION_KEYS + session.getId())).isFalse();
		pending.setLastAccessedTime(Instant.now());
		sessions.save(pending);
		assertThat(redis.hasKey(SESSION_KEYS + session.getId())).isFalse();
	}

	@Test
	void logoutStillRequiresTheRealCsrfToken() throws Exception {
		Cookie session = login(csrf());
		mvc.perform(post("/api/auth/logout").cookie(session)).andExpect(status().isForbidden());
		mvc.perform(post("/api/auth/logout").cookie(session, new Cookie("XSRF-TOKEN", "valid-cookie"))
				.header("X-XSRF-TOKEN", "wrong-header")).andExpect(status().isForbidden());
		mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isOk());
	}

	@Test
	void anUnexpectedRedisTypeErrorIsNotMisreportedAsAnonymous() {
		String id = java.util.UUID.randomUUID().toString();
		redis.opsForValue().set(SESSION_KEYS + id, "unexpected string, not a session hash");
		assertThatThrownBy(() -> sessions.findById(id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
	}

	@Test
	void cleaningAnInvalidSnapshotDoesNotDeleteARecordCompletedByAnotherWriter() {
		var initial = sessions.createSession();
		sessions.save(initial);
		String key = SESSION_KEYS + initial.getId();
		redis.opsForHash().delete(key, "creationTime");
		var real = defaults.getSessionRedisOperations();
		org.springframework.data.redis.core.HashOperations<String, String, Object> hashes = real.opsForHash();
		var intercepted = org.mockito.Mockito.spy(hashes);
		org.mockito.Mockito.doAnswer(invocation -> {
			Map<String, Object> snapshot = hashes.entries(key);
			hashes.put(key, "creationTime", initial.getCreationTime().toEpochMilli());
			return snapshot;
		}).when(intercepted).entries(key);
		var operations = org.mockito.Mockito.spy(real);
		org.mockito.Mockito.doReturn(intercepted).when(operations).opsForHash();
		var isolatedReader = new ConcurrentRedisSessionRepository(operations, ((SessionRepository<?>) defaults)::createSession,
				"carelink:session", org.springframework.session.FlushMode.ON_SAVE,
				org.springframework.session.SaveMode.ON_SET_ATTRIBUTE, org.springframework.session.UuidSessionIdGenerator.getInstance());
		assertThat(isolatedReader.findById(initial.getId())).isNull();
		assertThat(redis.hasKey(key)).isTrue();
		assertThat(sessions.findById(initial.getId())).isNotNull();
	}

	@Test
	void redisConnectionFailuresAreNotConvertedToSuccessfulSaves() {
		var operations = org.mockito.Mockito.spy(defaults.getSessionRedisOperations());
		org.mockito.Mockito.doThrow(new org.springframework.data.redis.RedisConnectionFailureException("simulated unavailable Redis"))
				.when(operations).execute(org.mockito.ArgumentMatchers.<org.springframework.data.redis.core.RedisCallback<Object>>any());
		var unavailable = new ConcurrentRedisSessionRepository(operations, ((SessionRepository<?>) defaults)::createSession,
				"carelink:session", org.springframework.session.FlushMode.ON_SAVE,
				org.springframework.session.SaveMode.ON_SET_ATTRIBUTE, org.springframework.session.UuidSessionIdGenerator.getInstance());
		var pending = unavailable.createSession();
		assertThatThrownBy(() -> unavailable.save(pending)).isInstanceOf(org.springframework.data.redis.RedisConnectionFailureException.class);
	}

	private Cookie csrf() throws Exception {
		Cookie token = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		return token;
	}

	private Cookie login(Cookie csrf) throws Exception {
		Cookie session = mvc.perform(post("/api/auth/login").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"session-check\",\"password\":\"test-password\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getCookie("SESSION");
		assertThat(session).as("the session cookie Spring Session sets").isNotNull();
		return session;
	}

	/** Spring Session writes the session id into its cookie Base64-encoded. */
	private static String sessionId(Cookie session) {
		return new String(Base64.getDecoder().decode(session.getValue()), StandardCharsets.UTF_8);
	}

}
