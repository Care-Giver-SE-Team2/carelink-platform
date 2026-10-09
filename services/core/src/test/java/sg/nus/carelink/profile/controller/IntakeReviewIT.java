package sg.nus.carelink.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;

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
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.testsupport.SharedMySql;

/**
 * The manager's intake review end to end on real MySQL: the pending list with its applicant,
 * sector and checks; approval creating the elder in one transaction; a decline keeping the reason;
 * a second answer refused; one elder, one record, at submission and at approval; and only a
 * manager allowed in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IntakeReviewIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, IntakeReviewIT.class, null);
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void seed() {
		jdbc.update("insert into app_user(id,username,password_hash,display_name) values"
				+ " (9001,'intake-manager','unused','Manager'),(9002,'intake-grace','unused','Grace'),"
				+ " (9003,'intake-cg','unused','Siti')");
		jdbc.update("insert into user_role(user_id,role) values (9001,'MANAGER'),(9002,'FAMILY'),(9003,'CAREGIVER')");
		jdbc.update("insert into family_member(id,user_id,full_name,phone) values (9101,9002,'Grace Tan Wei Ling','+65 9123 4488')");
		jdbc.update("insert into caregiver(id,user_id,full_name,sector,dialects,status) values"
				+ " (9201,9003,'Siti','S31-IT','Hokkien','AVAILABLE')");
		jdbc.update("insert into elder(id,full_name,postal_code,sector) values"
				+ " (9301,'Goh Bee Lian','990154','S31-IT'),(9302,'Ong Kim Bee','990221','S31-IT')");
		jdbc.update("insert into intake_application(id,applicant_family_member_id,target_elder_name,target_elder_age,"
				+ "target_address,postal_code,mobility_level,preferred_dialects,care_needs,status,created_at) values"
				+ " (9401,9101,'Tan Bee Choo',83,'Blk 230 Bishan St 23','990230','ASSISTIVE_CANE','Hokkien','[\"BATHING\"]','SUBMITTED','2026-10-05 01:00:00'),"
				+ " (9402,9101,'Goh Bee Lian',null,'Blk 154 Bishan St 11','990154','WHEELCHAIR_BEDBOUND',null,'[]','SUBMITTED','2026-10-04 05:13:00'),"
				+ " (9403,9101,'Lim Ah Kow',80,'Blk 1 Somewhere','990001','INDEPENDENT',null,'[]','REJECTED','2026-10-01 00:00:00')");
	}

	@Test
	void pendingApplicationsComeNewestFirstWithApplicantSectorAndChecks() throws Exception {
		mvc.perform(get("/api/intake-reviews").with(user("intake-manager").roles("MANAGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == 9403)]").isEmpty())
				.andExpect(jsonPath("$[?(@.id == 9401)].applicant.fullName").value("Grace Tan Wei Ling"))
				.andExpect(jsonPath("$[?(@.id == 9401)].applicant.username").value("intake-grace"))
				.andExpect(jsonPath("$[?(@.id == 9401)].sector").value("S31-IT"))
				.andExpect(jsonPath("$[?(@.id == 9401)].careNeeds[0]").value("BATHING"))
				.andExpect(jsonPath("$[?(@.id == 9401)].checks[?(@.key == 'dialect')].count").value(1))
				.andExpect(jsonPath("$[?(@.id == 9402)].checks[?(@.key == 'duplicate')]").isEmpty());
	}

	@Test
	void approvingCreatesTheElderAndDecliningKeepsTheReason() throws Exception {
		mvc.perform(post("/api/intake-reviews/9401/approve").with(user("intake-manager").roles("MANAGER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Welcome\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.elderId").isNumber())
				.andExpect(jsonPath("$.elderLogin.username").value("tan.bee.choo"));
		mvc.perform(post("/api/intake-reviews/9402/approve").with(user("intake-manager").roles("MANAGER")).with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ELDER_ALREADY_REGISTERED")); // Goh Bee Lian is elder 9301
		mvc.perform(post("/api/intake-reviews/9402/decline").with(user("intake-manager").roles("MANAGER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Already with us\"}"))
				.andExpect(status().isOk());

		Map<String, Object> approved = application(9401);
		assertThat(approved).containsEntry("status", "APPROVED").containsEntry("reviewed_by_user_id", 9001L)
				.containsEntry("review_remarks", "Welcome");
		assertThat(approved.get("reviewed_at")).isNotNull();
		Map<String, Object> elder = jdbc.queryForMap(
				"select full_name, postal_code, sector, mobility_level, preferred_dialects from elder where id = ?",
				approved.get("elder_id"));
		assertThat(elder).containsEntry("full_name", "Tan Bee Choo").containsEntry("postal_code", "990230")
				.containsEntry("sector", "S31-IT").containsEntry("mobility_level", "ASSISTIVE_CANE")
				.containsEntry("preferred_dialects", "Hokkien");
		assertThat(application(9402)).containsEntry("status", "REJECTED")
				.containsEntry("review_remarks", "Already with us").containsEntry("elder_id", null);

		mvc.perform(post("/api/intake-reviews/9401/approve").with(user("intake-manager").roles("MANAGER")).with(csrf()))
				.andExpect(status().isConflict());
		mvc.perform(post("/api/intake-reviews/9999/approve").with(user("intake-manager").roles("MANAGER")).with(csrf()))
				.andExpect(status().isNotFound());
	}

	@Test
	void theElderCanLogInWithTheTemporaryPasswordFromTheApproval() throws Exception {
		jdbc.update("insert into app_user(id,username,password_hash,display_name) values (9004,'tan.bee.choo','unused','Taken')");

		String body = mvc.perform(post("/api/intake-reviews/9401/approve").with(user("intake-manager").roles("MANAGER"))
				.with(csrf()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Map<String, Object> login = JsonPath.read(body, "$.elderLogin");
		assertThat(login).containsEntry("username", "tan.bee.choo2"); // the plain name was taken

		Map<String, Object> elder = jdbc.queryForMap(
				"select e.user_id, u.username, u.display_name, r.role from elder e join app_user u on u.id = e.user_id"
						+ " join user_role r on r.user_id = u.id where e.id = ?",
				application(9401).get("elder_id"));
		assertThat(elder).containsEntry("username", "tan.bee.choo2").containsEntry("display_name", "Tan Bee Choo")
				.containsEntry("role", "ELDER");
		assertThat(jdbc.queryForObject("select password_hash from app_user where username = 'tan.bee.choo2'", String.class))
				.isNotEqualTo(login.get("temporaryPassword"));

		mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"tan.bee.choo2\",\"password\":\"" + login.get("temporaryPassword") + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles[0]").value("ELDER"));
	}

	@Test
	void aFamilyCannotApplyForSomeoneAlreadyOnRecordOrAlreadyAppliedFor() throws Exception {
		mvc.perform(post("/api/intake-applications").with(user("intake-grace").roles("FAMILY")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"targetElderName\":\"ong kim  bee\",\"targetAddress\":\"Blk 221\",\"postalCode\":\"990221\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ELDER_ALREADY_REGISTERED"));
		mvc.perform(post("/api/intake-applications").with(user("intake-grace").roles("FAMILY")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"targetElderName\":\"Tan Bee Choo\",\"targetAddress\":\"Blk 230\",\"postalCode\":\"990230\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPLICATION_ALREADY_SUBMITTED"));
		mvc.perform(post("/api/intake-applications").with(user("intake-grace").roles("FAMILY")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"targetElderName\":\"Someone New\",\"targetAddress\":\"Blk 9\",\"postalCode\":\"990009\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void declineWithoutAReasonIsRefused() throws Exception {
		mvc.perform(post("/api/intake-reviews/9401/decline").with(user("intake-manager").roles("MANAGER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"message\":\" \"}"))
				.andExpect(status().isConflict());
		assertThat(application(9401)).containsEntry("status", "SUBMITTED");
	}

	@Test
	void onlyAManagerMayReadOrAnswer() throws Exception {
		mvc.perform(get("/api/intake-reviews").with(user("intake-grace").roles("FAMILY")))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/intake-reviews/9401/approve").with(user("intake-grace").roles("FAMILY")).with(csrf()))
				.andExpect(status().isForbidden());
	}

	/** Flushes first: the test transaction holds the answer until it is written out. */
	private Map<String, Object> application(long id) {
		entityManager.flush();
		return jdbc.queryForMap(
				"select status, reviewed_by_user_id, review_remarks, reviewed_at, elder_id from intake_application where id = ?",
				id);
	}
}
