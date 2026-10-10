package sg.nus.carelink.rostering.infrastructure.visit;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.visitapi.VisitApi;

/**
 * UC-MG06 with visit outside core, on real MySQL: core has {@code carelink.visit-api.base-url} set,
 * so rostering counts the visits at risk from what visit's internal API answers, and core's
 * database holds no visit at all. visit is a test double ({@code @MockitoBean VisitApi}), the way a
 * core test that needs visit runs once visit has moved out. The register and the plans are the
 * ones in CredentialReviewIT, which runs the same count with visit in core, and the answer is the
 * same. Today is 2026-10-02, 09:15 in Singapore.
 */
@SpringBootTest(properties = "carelink.visit-api.base-url=http://visit.invalid")
@AutoConfigureMockMvc
@Transactional
@Import(VisitsAtRiskWithVisitOutsideCoreIT.FixedTime.class)
class VisitsAtRiskWithVisitOutsideCoreIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, VisitsAtRiskWithVisitOutsideCoreIT.class, null);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTime {
		@Bean
		@Primary
		Clock reviewClock() {
			return Clock.fixed(Instant.parse("2026-10-02T01:15:00Z"), ZoneId.of("Asia/Singapore"));
		}
	}

	@MockitoBean
	private VisitApi visit;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

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
	}

	@Test
	void theVisitsAtRiskAreCountedFromWhatVisitAnswers() throws Exception {
		// What visit answers for CredentialReviewIT's visits: one before the old certificate lapses,
		// two after it. The cancelled one is not booked, so visit leaves it out.
		given(visit.unstartedBetween(LocalDateTime.of(2026, 10, 2, 9, 15), LocalDateTime.of(2026, 10, 16, 0, 0)))
				.willReturn(List.of(new VisitApi.Assignment(1L, 8101L, 8501L, LocalDateTime.of(2026, 10, 5, 8, 0)),
						new VisitApi.Assignment(2L, 8101L, 8501L, LocalDateTime.of(2026, 10, 6, 8, 0)),
						new VisitApi.Assignment(3L, 8101L, 8501L, LocalDateTime.of(2026, 10, 7, 8, 0))));

		mvc.perform(get("/api/credentials/visits-at-risk").with(user("cert-manager").roles("MANAGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.credentialId == 8302)].visitsAtRisk").value(2))
				.andExpect(jsonPath("$[?(@.credentialId == 8303)].visitsAtRisk").value(0));

		// From now to the end of the 14-day roster window
		verify(visit).unstartedBetween(LocalDateTime.of(2026, 10, 2, 9, 15), LocalDateTime.of(2026, 10, 16, 0, 0));
	}

}
