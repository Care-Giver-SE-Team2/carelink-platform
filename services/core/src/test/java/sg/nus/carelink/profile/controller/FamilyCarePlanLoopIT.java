package sg.nus.carelink.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import sg.nus.carelink.testsupport.SharedMySql;
import tools.jackson.databind.json.JsonMapper;

/**
 * The family's side of a care plan against a real MySQL, through the real security chain: a
 * service application is answered by the manager publishing a plan that includes its activities,
 * or declined with a reason; the family is notified when a version is published and can read it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FamilyCarePlanLoopIT {

	private static final String APPLICATIONS = "/api/family/service-applications";

	private final JsonMapper json = JsonMapper.builder().build();
	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyCarePlanLoopIT.class, null, "connectionTimeZone=Asia/Singapore");
	}

	/** Both tests share the database, so seed once; each test reads its own elder. */
	@BeforeEach
	void seed() {
		if (jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id = 7", Integer.class) > 0) {
			return;
		}
		jdbc.update("INSERT INTO app_user(id,username,password_hash,display_name) VALUES "
				+ "(7,'family','{noop}test','Family'),(8,'stranger','{noop}test','Stranger'),"
				+ "(9,'mei.ling','{noop}test','Tan Mei Ling')");
		jdbc.update("INSERT INTO user_role(user_id,role) VALUES (7,'FAMILY'),(8,'FAMILY'),(9,'MANAGER')");
		jdbc.update("INSERT INTO family_member(id,user_id,full_name) VALUES (42,7,'Family'),(43,8,'Stranger')");
		jdbc.update("INSERT INTO elder(id,full_name,address,postal_code) VALUES "
				+ "(1,'Tan Mei','12 Example Road','123456'),(2,'Lim Wei','34 Example Road','654321')");
		jdbc.update("INSERT INTO elder_family_binding(elder_id,family_member_id,status,access_scope) VALUES "
				+ "(1,42,'ACTIVE','FULL'),(2,42,'ACTIVE','READ_ONLY')");
	}

	@Test
	void publishingAPlanAnswersTheApplicationTellsTheFamilyAndShowsThemThePlan() throws Exception {
		long partly = submit("[\"BATHING\",\"LIGHT_EXERCISE\"]");
		family(get(APPLICATIONS + "/" + partly)).andExpect(jsonPath("$.outcome").value("SUBMITTED"));

		LocalDate starts = LocalDate.now(ZoneId.of("Asia/Singapore")).plusDays(7);
		long planId = publishBathing(starts);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE recipient_user_id = 7 "
				+ "AND event_type = 'CARE_PLAN_PUBLISHED' AND resource_type = 'CARE_PLAN' AND resource_id = ?",
				Integer.class, planId)).isEqualTo(1);
		family(get("/api/notifications/me"))
				.andExpect(jsonPath("$.items[0].title").value("Care plan v1 for Tan Mei is ready"))
				.andExpect(jsonPath("$.items[0].elderId").value(1));

		family(get("/api/family/elders/1/care-plan"))
				.andExpect(jsonPath("$.current").doesNotExist())
				.andExpect(jsonPath("$.upcoming.version").value(1))
				.andExpect(jsonPath("$.upcoming.effectiveFrom").value(starts.toString()))
				.andExpect(jsonPath("$.upcoming.tasks[0].activityCode").value("BATHING"))
				.andExpect(jsonPath("$.upcoming.tasks[0].slots[0].day").value("MONDAY"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor_user_id = 7 AND resource_type = 'CARE_PLAN'",
				Integer.class)).isEqualTo(1);

		family(get(APPLICATIONS + "/" + partly))
				.andExpect(jsonPath("$.outcome").value("SUBMITTED"))
				.andExpect(jsonPath("$.needs[0].plannedVersion").value(1))
				.andExpect(jsonPath("$.needs[1].plannedVersion").doesNotExist());
		long bathingOnly = submit("[\"BATHING\"]");
		family(get(APPLICATIONS + "/" + bathingOnly)).andExpect(jsonPath("$.outcome").value("PLANNED"));

		decline(bathingOnly, "Not needed").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SERVICE_APPLICATION_ALREADY_PLANNED"));
		decline(partly, "No exercise coach in your sector yet").andExpect(status().isNoContent());
		decline(partly, "Again").andExpect(status().isConflict());
		family(get(APPLICATIONS + "/" + partly))
				.andExpect(jsonPath("$.status").value("DECLINED"))
				.andExpect(jsonPath("$.outcome").value("DECLINED"))
				.andExpect(jsonPath("$.declineReason").value("No exercise coach in your sector yet"));
		mvc.perform(get("/api/elders/1/care-requests").with(user("mei.ling").roles("MANAGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.applicationId == " + partly + ")].outcome").value("DECLINED"))
				.andExpect(jsonPath("$[?(@.applicationId == " + bathingOnly + ")].outcome").value("PLANNED"));
	}

	@Test
	void onlyABoundFamilyMemberCanReadThePlanAndReadOnlyAccessIsEnough() throws Exception {
		mvc.perform(get("/api/family/elders/2/care-plan").with(user("stranger").roles("FAMILY")))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/family/elders/2/care-plan").with(user("family").roles("FAMILY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.current").doesNotExist())
				.andExpect(jsonPath("$.upcoming").doesNotExist());
	}

	private long submit(String careNeeds) throws Exception {
		String response = mvc.perform(post(APPLICATIONS).with(user("family").roles("FAMILY")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"elderId\":1,\"careNeeds\":" + careNeeds + ",\"notes\":null}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(response).get("id").longValue();
	}

	private long publishBathing(LocalDate starts) throws Exception {
		String draft = mvc.perform(post("/api/care-plans").with(user("mei.ling").roles("MANAGER")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{\"elderId\":1}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long planId = json.readTree(draft).get("id").longValue();
		mvc.perform(post("/api/care-plans/" + planId + "/publish").with(user("mei.ling").roles("MANAGER")).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"startDate\":\"" + starts + "\",\"nodes\":[{\"groupName\":\"Personal care\","
								+ "\"activityCode\":\"BATHING\",\"name\":\"Bathing assistance\","
								+ "\"visits\":[{\"day\":\"Mon\",\"startTime\":\"08:00\",\"minutes\":30}],"
								+ "\"evidenceType\":\"CHECKLIST\"}]}"))
				.andExpect(status().isOk());
		return planId;
	}

	private org.springframework.test.web.servlet.ResultActions decline(long id, String reason) throws Exception {
		return mvc.perform(post("/api/service-applications/" + id + "/decline")
				.with(user("mei.ling").roles("MANAGER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"));
	}

	private org.springframework.test.web.servlet.ResultActions family(
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.with(user("family").roles("FAMILY"))).andExpect(status().isOk());
	}
}
