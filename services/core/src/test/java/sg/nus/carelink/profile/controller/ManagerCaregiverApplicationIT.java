package sg.nus.carelink.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.testsupport.SharedMySql;

/** The manager's side of caregiver admission over real HTTP and MySQL: list, approve, reject. */
@SpringBootTest(properties = {"carelink.caregiver.expiry-scan-cron=-", "carelink.missed-check-in.enabled=false",
		"carelink.roster.schedule-cron=-", "carelink.report.schedule-cron=-"})
@AutoConfigureMockMvc
@Import(ManagerCaregiverApplicationIT.FixedClock.class)
class ManagerCaregiverApplicationIT {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final RequestPostProcessor MANAGER = user("review.manager").roles("MANAGER");

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, ManagerCaregiverApplicationIT.class, "+08:00");
	}

	@TestConfiguration
	static class FixedClock {
		@Bean
		@Primary
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-10-12T01:00:00Z"), ZoneOffset.UTC);
		}
	}

	@Autowired
	MockMvc mvc;
	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	void clear() {
		jdbc.update("delete from caregiver_admission_receipt");
		jdbc.update("delete from caregiver_application_history");
		jdbc.update("delete from caregiver_application");
		jdbc.update("delete from caregiver_admission_state");
		jdbc.update("delete from audit_log");
		jdbc.update("delete from caregiver");
		jdbc.update("delete from user_role");
		jdbc.update("delete from app_user");
		jdbc.update("insert into app_user(id,username,password_hash,display_name,enabled) values (73001,'review.manager','{noop}test-password','Review Manager',true)");
		jdbc.update("insert into user_role(user_id,role) values (73001,'MANAGER')");
	}

	@Test
	void anApplicationFromTheCaregiverAppIsListedForTheManagerAndApprovalAdmitsThem() throws Exception {
		register("review.a", "Lim Hui Min");

		var list = mvc.perform(get("/api/caregiver-applications").with(MANAGER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].username").value("review.a"))
				.andExpect(jsonPath("$.items[0].fullName").value("Lim Hui Min"))
				.andExpect(jsonPath("$.items[0].requestedSector").value("AMK"))
				.andExpect(jsonPath("$.items[0].certificates").isEmpty())
				.andReturn();
		var item = JSON.readTree(list.getResponse().getContentAsString()).path("items").path(0);
		long id = item.path("id").asLong();
		long caregiverId = item.path("caregiverId").asLong();

		mvc.perform(post("/api/caregiver-applications/" + id + "/approve").with(MANAGER).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(approval(item.path("version").asLong())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.caregiverId").value(caregiverId))
				.andExpect(jsonPath("$.status").value("APPROVED"));

		assertThat(jdbc.queryForObject("select status from caregiver where id=?", String.class, caregiverId))
				.isEqualTo("AVAILABLE");
		mvc.perform(get("/api/caregiver-applications").with(MANAGER))
				.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void rejectionNeedsTheCurrentVersionAndLeavesTheApplicantOnboarding() throws Exception {
		register("review.b", "Priya Nair");
		var item = JSON.readTree(mvc.perform(get("/api/caregiver-applications").with(MANAGER))
				.andReturn().getResponse().getContentAsString()).path("items").path(0);
		long id = item.path("id").asLong();
		long version = item.path("version").asLong();

		mvc.perform(post("/api/caregiver-applications/" + id + "/reject").with(MANAGER).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(rejection("First aid scan is unreadable", version + 1)))
				.andExpect(status().isConflict());

		mvc.perform(post("/api/caregiver-applications/" + id + "/reject").with(MANAGER).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(rejection("First aid scan is unreadable", version)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));

		assertThat(jdbc.queryForObject("select status from caregiver where id=?", String.class,
				item.path("caregiverId").asLong())).isEqualTo("ONBOARDING");
		mvc.perform(get("/api/caregiver-applications").with(MANAGER))
				.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void onlyManagersReviewApplications() throws Exception {
		mvc.perform(get("/api/caregiver-applications").with(user("review.a").roles("CAREGIVER")))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/caregiver-applications/1/approve").with(MANAGER)
				.contentType(MediaType.APPLICATION_JSON).content(approval(0)))
				.andExpect(status().isForbidden()); // no CSRF token
	}

	private void register(String username, String fullName) throws Exception {
		mvc.perform(post("/api/caregiver-registrations").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + username + "\",\"password\":\"chosen-password\",\"fullName\":\""
						+ fullName + "\",\"phone\":\"91234567\",\"requestedSector\":\"AMK\"}"))
				.andExpect(status().isCreated());
	}

	private static String approval(long version) {
		return "{\"expectedVersion\":" + version + ",\"clientRequestId\":\"" + UUID.randomUUID() + "\"}";
	}

	private static String rejection(String reason, long version) {
		return "{\"reason\":\"" + reason + "\",\"expectedVersion\":" + version + ",\"clientRequestId\":\""
				+ UUID.randomUUID() + "\"}";
	}
}
