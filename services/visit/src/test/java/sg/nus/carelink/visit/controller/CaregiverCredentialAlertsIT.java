package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.profile.application.CaregiverDirectory;
import sg.nus.carelink.testsupport.SharedMySql;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(CaregiverCredentialAlertsIT.FixedTime.class)
class CaregiverCredentialAlertsIT {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, CaregiverCredentialAlertsIT.class, "+05:00");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired CaregiverDirectory familyDirectory;

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTime {
        @Bean @Primary Clock credentialClock() {
            return Clock.fixed(Instant.parse("2026-09-24T16:30:00Z"), ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void seedIsolatedAccounts() {
        jdbc.update("insert into app_user(id,username,password_hash,display_name) values (7001,'credential-a','unused','A'),(7002,'credential-b','unused','B')");
        jdbc.update("insert into user_role(user_id,role) values (7001,'CAREGIVER'),(7002,'CAREGIVER')");
        jdbc.update("insert into caregiver(id,user_id,full_name,status) values (7101,7001,'A','AVAILABLE'),(7102,7002,'B','AVAILABLE')");
        jdbc.update("insert into credential_type(id,name) values (7201,'Demo renewal')");
        credential(7301,7101,"PUBLISHED",null,TODAY.minusDays(1),null);
        credential(7302,7102,"PUBLISHED",null,TODAY,null);
    }

    private void credential(long id, long owner, String state, LocalDate from, LocalDate expiry, Long renews) {
        jdbc.update("insert into credential(id,caregiver_id,credential_type_id,certificate_no,valid_from,expiry_date,status,renews_credential_id) values (?,?,7201,?,?,?,?,?)",
                id,owner,"PRIVATE-CERT-"+id,from,expiry,state,renews);
    }

    private ResultActions schedule(String username, LocalDate from) throws Exception {
        return mvc.perform(get("/api/caregivers/me/schedule").with(user(username).roles("CAREGIVER"))
                .param("dateFrom",from.toString()).param("dateTo",from.toString()));
    }

    @ParameterizedTest
    @CsvSource({"SUBMITTED,PENDING_REVIEW", "REJECTED,REJECTED", "REVOKED,REVOKED"})
    void retainsOldReminderAndReportsRenewalWithoutWritingCredentials(String state, String renewal) throws Exception {
        credential(7303,7101,state,null,TODAY.plusDays(365),7301L);
        var before = jdbc.queryForList("select * from credential order by id");
        schedule("credential-a",TODAY.minusMonths(2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialAlertContext.asOfDate").value("2026-09-25"))
                .andExpect(jsonPath("$.credentialAlertContext.warningDays").value(30))
                .andExpect(jsonPath("$.credentialAlertContext.reviewRequired").value(false))
                .andExpect(jsonPath("$.certificationAlerts.length()").value(1))
                .andExpect(jsonPath("$.certificationAlerts[0].id").value(7301))
                .andExpect(jsonPath("$.certificationAlerts[0].daysUntilExpiry").value(-1))
                .andExpect(jsonPath("$.certificationAlerts[0].renewalState").value(renewal));
        assertThat(jdbc.queryForList("select * from credential order by id")).isEqualTo(before);
    }

    @Test
    void futureRenewalBecomesEffectiveWithoutChangingPublicFamilyProjection() throws Exception {
        credential(7303,7101,"PUBLISHED",TODAY.plusDays(7),TODAY.plusDays(365),7301L);
        schedule("credential-a",TODAY).andExpect(status().isOk())
                .andExpect(jsonPath("$.certificationAlerts[0].renewalState").value("APPROVED_NOT_EFFECTIVE"))
                .andExpect(jsonPath("$.certificationAlerts[0].renewalValidFrom").value("2026-10-02"));
        jdbc.update("update credential set valid_from=? where id=7303",TODAY);
        // JDBC bypasses JPA's first-level cache in this test-wide transaction.
        // Real HTTP requests have separate persistence contexts; emulate that boundary.
        entityManager.clear();
        var publicBefore = familyDirectory.listPublicCredentials(7101L);
        schedule("credential-a",TODAY).andExpect(status().isOk()).andExpect(jsonPath("$.certificationAlerts").isEmpty());
        assertThat(familyDirectory.listPublicCredentials(7101L)).isEqualTo(publicBefore).hasSize(2);
        assertThat(JsonMapper.builder().build().writeValueAsString(publicBefore))
                .doesNotContain("certificateNo", "renewsCredentialId", "renewalState", "PRIVATE-CERT");
    }

    @Test
    void foreignRenewalCannotClearAnotherCaregiversReminder() throws Exception {
        credential(7303,7102,"PUBLISHED",null,TODAY.plusDays(365),7301L);
        var response = schedule("credential-a",TODAY).andExpect(status().isOk())
                .andExpect(jsonPath("$.certificationAlerts.length()").value(1))
                .andExpect(jsonPath("$.certificationAlerts[0].id").value(7301)).andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("PRIVATE-CERT-7302", "PRIVATE-CERT-7303");
        schedule("credential-b",TODAY).andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialAlertContext.reviewRequired").value(true))
                .andExpect(jsonPath("$.certificationAlerts[0].id").value(7302));
    }

    @Test
    void cyclicRowsKeepWarningsAndExposeOnlyAGenericReviewFlag() throws Exception {
        credential(7303,7101,"PUBLISHED",null,TODAY,7301L);
        jdbc.update("update credential set renews_credential_id=7303 where id=7301");
        schedule("credential-a",TODAY).andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialAlertContext.reviewRequired").value(true))
                .andExpect(jsonPath("$.certificationAlerts.length()").value(2))
                .andExpect(jsonPath("$.certificationAlerts[0].renewalState").value("CHECK_REQUIRED"));
    }

    @Test
    void inclusiveWindowAndPermanentCredentialsUseServerSingaporeDate() throws Exception {
        credential(7303,7101,"PUBLISHED",null,TODAY, null);
        credential(7304,7101,"PUBLISHED",null,TODAY.plusDays(30), null);
        credential(7305,7101,"PUBLISHED",null,TODAY.plusDays(31), null);
        credential(7306,7101,"PUBLISHED",null,LocalDate.of(9999,12,31), null);
        schedule("credential-a",TODAY).andExpect(status().isOk())
                .andExpect(jsonPath("$.certificationAlerts.length()").value(3))
                .andExpect(jsonPath("$.certificationAlerts[1].daysUntilExpiry").value(0))
                .andExpect(jsonPath("$.certificationAlerts[2].daysUntilExpiry").value(30));
    }
}
