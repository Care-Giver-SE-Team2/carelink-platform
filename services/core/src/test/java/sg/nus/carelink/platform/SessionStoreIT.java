package sg.nus.carelink.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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

		assertThat(redis.hasKey(SESSION_KEYS + sessionId(session))).isTrue();
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
