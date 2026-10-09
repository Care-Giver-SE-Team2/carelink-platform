package sg.nus.carelink.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import sg.nus.carelink.testsupport.SharedMySql;

/**
 * The Applications screen's demonstration script loads, can be loaded again, and gives the manager
 * the picture its header promises: five pending applications with a spread of checks and
 * submission times that are hours, not a time zone, ago. Loaded as a script on one connection,
 * as DemoSeedIT does, through the same JDBC time-zone setting the application runs with.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ManagerApplicationsDemoIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, ManagerApplicationsDemoIT.class, null, "connectionTimeZone=Asia/Singapore");
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void givesTheManagerTheScenarioAndReopensDeclinedApplicationsOnASecondLoad() throws Exception {
		load();
		long users = count("app_user");
		long elders = count("elder");
		long applications = count("intake_application");

		String body = pending();
		assertThat(JsonPath.<List<String>>read(body, "$[*].targetElderName")).containsExactly(
				"Tan Bee Choo", "Mohd Yusof bin Ali", "Lim Soo Hwa", "Ng Kim Lan", "Lakshmi Raman");
		assertThat(sectorOf(body, "Tan Bee Choo")).isEqualTo("AMK");
		assertThat(sectorOf(body, "Mohd Yusof bin Ali")).isEqualTo("TPY");
		assertThat(sectorOf(body, "Ng Kim Lan")).isNull();
		assertThat(failedChecks(body, "Tan Bee Choo")).isEmpty();
		assertThat(failedChecks(body, "Mohd Yusof bin Ali")).isEmpty();
		assertThat(failedChecks(body, "Lim Soo Hwa")).containsExactly("dialect");
		assertThat(failedChecks(body, "Ng Kim Lan")).containsExactly("contact", "sector", "dialect");
		assertThat(failedChecks(body, "Lakshmi Raman")).isEmpty();

		// Submitted three hours ago in real time, not three hours give or take the eight between UTC and Singapore.
		OffsetDateTime submitted = OffsetDateTime.parse(
				JsonPath.<List<String>>read(body, "$[?(@.targetElderName == 'Tan Bee Choo')].createdAt").get(0));
		assertThat(Duration.between(submitted, OffsetDateTime.now()).toMinutes()).isBetween(170L, 190L);

		jdbc.update("insert into app_user(username,password_hash,display_name) values ('demo-it-manager','unused','M')");
		jdbc.update("insert into user_role(user_id,role) select id,'MANAGER' from app_user where username='demo-it-manager'");
		mvc.perform(post("/api/intake-reviews/" + idOf(body, "Tan Bee Choo") + "/approve")
				.with(user("demo-it-manager").roles("MANAGER")).with(csrf())).andExpect(status().isOk());
		mvc.perform(post("/api/intake-reviews/" + idOf(body, "Ng Kim Lan") + "/decline")
				.with(user("demo-it-manager").roles("MANAGER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Outside our area\"}"))
				.andExpect(status().isOk());
		assertThat(JsonPath.<List<String>>read(pending(), "$[*].targetElderName"))
				.doesNotContain("Tan Bee Choo", "Ng Kim Lan");

		load();

		assertThat(count("intake_application")).isEqualTo(applications);
		// Only the approval's own elder and login are new; the script added nothing.
		assertThat(count("elder")).isEqualTo(elders + 1);
		assertThat(count("app_user")).isEqualTo(users + 2);
		List<String> reopened = JsonPath.read(pending(), "$[*].targetElderName");
		assertThat(reopened).contains("Ng Kim Lan").doesNotContain("Tan Bee Choo");
	}

	private String pending() throws Exception {
		return mvc.perform(get("/api/intake-reviews").with(user("demo-it-manager").roles("MANAGER")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static long idOf(String body, String elder) {
		return JsonPath.<List<Integer>>read(body, "$[?(@.targetElderName == '" + elder + "')].id").get(0).longValue();
	}

	private static String sectorOf(String body, String elder) {
		return JsonPath.<List<String>>read(body, "$[?(@.targetElderName == '" + elder + "')].sector").get(0);
	}

	private static List<String> failedChecks(String body, String elder) {
		List<Map<String, Object>> checks =
				JsonPath.<List<List<Map<String, Object>>>>read(body, "$[?(@.targetElderName == '" + elder + "')].checks").get(0);
		return checks.stream().filter(c -> !(Boolean) c.get("pass")).map(c -> (String) c.get("key")).toList();
	}

	private void load() {
		jdbc.execute((ConnectionCallback<Void>) connection -> {
			ScriptUtils.executeSqlScript(connection, new EncodedResource(
					new ClassPathResource("db/demo/manager-applications.sql"), StandardCharsets.UTF_8));
			return null;
		});
	}

	private long count(String table) {
		Long rows = jdbc.queryForObject("select count(*) from " + table, Long.class);
		return rows == null ? 0L : rows;
	}
}
