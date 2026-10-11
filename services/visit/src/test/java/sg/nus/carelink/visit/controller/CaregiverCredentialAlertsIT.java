package sg.nus.carelink.visit.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.testsupport.CoreStandIn;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * The caregiver's schedule shows the credential reminders core answers for the caregiver, asked for
 * on the server's Singapore date. Which reminders there are is core's alert policy, and
 * CaregiverCredentialAlertsIT in core covers it; here core is its stand-in.
 */
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
    @Autowired CoreStandIn core;

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTime {
        // 00:30 on the 25th in Singapore, still the 24th in UTC
        @Bean @Primary Clock credentialClock() {
            return Clock.fixed(Instant.parse("2026-09-24T16:30:00Z"), ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void seedIsolatedAccounts() {
        jdbc.update("insert into app_user(id,username,password_hash,display_name) values (7001,'credential-a','unused','A'),(7002,'credential-b','unused','B')");
        jdbc.update("insert into user_role(user_id,role) values (7001,'CAREGIVER'),(7002,'CAREGIVER')");
        jdbc.update("insert into caregiver(id,user_id,full_name,status) values (7101,7001,'A','AVAILABLE'),(7102,7002,'B','AVAILABLE')");
    }

    private ResultActions schedule(String username) throws Exception {
        return mvc.perform(get("/api/caregivers/me/schedule").with(user(username).roles("CAREGIVER"))
                .param("dateFrom",TODAY.toString()).param("dateTo",TODAY.toString()));
    }

    @Test
    void asksCoreOnTheSingaporeDate() throws Exception {
        schedule("credential-a").andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialAlertContext.asOfDate").value("2026-09-25"))
                .andExpect(jsonPath("$.credentialAlertContext.warningDays").value(30))
                .andExpect(jsonPath("$.credentialAlertContext.reviewRequired").value(false))
                .andExpect(jsonPath("$.certificationAlerts").isEmpty());
    }

    @Test
    void showsWhatCoreAnswersToItsOwnCaregiverOnly() throws Exception {
        core.alerts(7101L, new CoreApi.CredentialAlerts(List.of(new CoreApi.CredentialAlert(7301L, "Demo renewal",
                "PRIVATE-CERT-7301", TODAY.minusDays(1), "PUBLISHED", "EXPIRED", -1, "PENDING_REVIEW", null)),
                new CoreApi.CredentialAlertContext(TODAY, 30, true)));
        schedule("credential-a").andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialAlertContext.reviewRequired").value(true))
                .andExpect(jsonPath("$.certificationAlerts.length()").value(1))
                .andExpect(jsonPath("$.certificationAlerts[0].id").value(7301))
                .andExpect(jsonPath("$.certificationAlerts[0].daysUntilExpiry").value(-1))
                .andExpect(jsonPath("$.certificationAlerts[0].renewalState").value("PENDING_REVIEW"));
        schedule("credential-b").andExpect(status().isOk())
                .andExpect(jsonPath("$.certificationAlerts").isEmpty());
    }
}
