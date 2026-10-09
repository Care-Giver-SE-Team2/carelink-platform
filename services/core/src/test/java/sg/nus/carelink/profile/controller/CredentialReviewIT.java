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
import java.util.Map;

import jakarta.persistence.EntityManager;

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
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.testsupport.SharedMySql;

/**
 * UC-MG06 end to end on real MySQL: V11 applies, the register folds a pending renewal into
 * one row, rostering counts the visits it puts at risk, and each review is stored with who
 * and when. Today is 2026-10-02.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(CredentialReviewIT.FixedTime.class)
class CredentialReviewIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, CredentialReviewIT.class, null);
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private EntityManager entityManager;

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTime {
		@Bean
		@Primary
		Clock reviewClock() {
			return Clock.fixed(Instant.parse("2026-10-02T01:15:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void seed() {
		jdbc.update("insert into app_user(id,username,password_hash,display_name) values"
				+ " (8001,'cert-manager','unused','Manager'),(8002,'cert-devi','unused','Devi'),(8003,'cert-rosnah','unused','Rosnah')");
		jdbc.update("insert into user_role(user_id,role) values (8001,'MANAGER'),(8002,'CAREGIVER'),(8003,'CAREGIVER')");
		jdbc.update("insert into caregiver(id,user_id,full_name,status) values"
				+ " (8101,8002,'Devi Raman','AVAILABLE'),(8102,8003,'Rosnah Binte Ali','AVAILABLE')");
		jdbc.update("insert into credential_type(id,name) values (8201,'First aid IT'),(8202,'Dementia care IT')");
		jdbc.update("insert into credential(id,caregiver_id,credential_type_id,certificate_no,expiry_date,status,renews_credential_id) values"
				+ " (8301,8101,8201,'OLD-FA','2026-10-05','PUBLISHED',null),"
				+ " (8302,8101,8201,'SRC-FA-88412','2028-08-27','SUBMITTED',8301),"
				+ " (8303,8102,8202,'DC-1','2029-01-01','SUBMITTED',null)");
		jdbc.update("insert into elder(id,full_name) values (8401,'Chan Bee Choo')");
		jdbc.update("insert into care_plan(id,elder_id,version,status) values (8501,8401,1,'PUBLISHED')");
		jdbc.update("insert into care_plan_required_credential(care_plan_id,credential_type_id) values (8501,8201)");
		// Before the old certificate lapses, two after it, and one after that nobody is going to.
		jdbc.update("insert into visit(elder_id,caregiver_id,care_plan_id,scheduled_start,status) values"
				+ " (8401,8101,8501,'2026-10-05 08:00:00','SCHEDULED'),"
				+ " (8401,8101,8501,'2026-10-06 08:00:00','SCHEDULED'),"
				+ " (8401,8101,8501,'2026-10-07 08:00:00','SCHEDULED'),"
				+ " (8401,8101,8501,'2026-10-08 08:00:00','CANCELLED')");
	}

	@Test
	void theRegisterShowsAPendingRenewalAgainstTheExpiryOfTheCertificateItReplaces() throws Exception {
		mvc.perform(get("/api/credentials").with(user("cert-manager").roles("MANAGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == 8301)]").isEmpty())
				.andExpect(jsonPath("$[?(@.id == 8302)].state").value("SUBMITTED"))
				.andExpect(jsonPath("$[?(@.id == 8302)].renewal").value(true))
				.andExpect(jsonPath("$[?(@.id == 8302)].caregiverName").value("Devi Raman"))
				.andExpect(jsonPath("$[?(@.id == 8302)].daysUntilExpiry").value(3))
				.andExpect(jsonPath("$[?(@.id == 8302)].expiring").value(true))
				.andExpect(jsonPath("$[?(@.id == 8303)].renewal").value(false));
	}

	@Test
	void visitsAtRiskAreTheBookedOnesAfterTheLapseOnPlansThatNeedTheType() throws Exception {
		mvc.perform(get("/api/credentials/visits-at-risk").with(user("cert-manager").roles("MANAGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.credentialId == 8302)].visitsAtRisk").value(2))
				.andExpect(jsonPath("$[?(@.credentialId == 8303)].visitsAtRisk").value(0));
	}

	@Test
	void eachReviewIsStoredWithTheManagerAndTheTime() throws Exception {
		mvc.perform(post("/api/credentials/8302/publish").with(user("cert-manager").roles("MANAGER")).with(csrf()))
				.andExpect(status().isNoContent());
		mvc.perform(post("/api/credentials/8303/reject").with(user("cert-manager").roles("MANAGER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Issuer is not accredited\"}"))
				.andExpect(status().isNoContent());

		Map<String, Object> published = row(8302);
		assertThat(published).containsEntry("status", "PUBLISHED").containsEntry("reviewed_by_user_id", 8001L);
		assertThat(published.get("reviewed_at")).isNotNull();
		assertThat(row(8303)).containsEntry("status", "REJECTED").containsEntry("review_note", "Issuer is not accredited");

		mvc.perform(post("/api/credentials/8302/publish").with(user("cert-manager").roles("MANAGER")).with(csrf()))
				.andExpect(status().isConflict());
	}

	@Test
	void onlyAManagerMayReadOrReview() throws Exception {
		mvc.perform(get("/api/credentials").with(user("cert-devi").roles("CAREGIVER")))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/credentials/8302/publish").with(user("cert-devi").roles("CAREGIVER")).with(csrf()))
				.andExpect(status().isForbidden());
	}

	/** Flushes first: the test transaction holds the review until it is written out. */
	private Map<String, Object> row(long id) {
		entityManager.flush();
		return jdbc.queryForMap("select status, reviewed_by_user_id, review_note, reviewed_at from credential where id = ?", id);
	}
}
