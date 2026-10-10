package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import sg.nus.carelink.testsupport.SharedMySql;

@SpringBootTest
@AutoConfigureMockMvc
class CaregiverWorkIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, CaregiverWorkIT.class, null, "connectionTimeZone=LOCAL");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    long aVisit;
    long bVisit;
    LocalDate day;

    @BeforeEach void seed() {
        jdbc.update("insert ignore into app_user(username,password_hash,display_name) values('existing-person','unused','Existing person')");
        jdbc.update("insert into elder(full_name) select 'Existing elder' where not exists (select 1 from elder where full_name='Existing elder')");
        loadSeed();
        aVisit = jdbc.queryForObject("select id from visit where service_type='DEMO_MORNING_CARE'",Long.class);
        bVisit = jdbc.queryForObject("select id from visit where service_type='DEMO_REASSIGNED_CARE'",Long.class);
        day = jdbc.queryForObject("select date(scheduled_start) from visit where id=?",LocalDate.class,aVisit);
    }
    void loadSeed() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection,new EncodedResource(
                new ClassPathResource("db/demo/caregiver-slice1.sql"),StandardCharsets.UTF_8));
            return null;
        });
    }
    @Test void seedIsRepeatableAndDoesNotNeedFixedIds() {
        long before = jdbc.queryForObject("select count(*) from visit",Long.class);
        loadSeed();
        assertThat(jdbc.queryForObject("select count(*) from visit",Long.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from care_plan where status='PUBLISHED'",Long.class)).isEqualTo(1);
    }
    @Test void realPasswordLoginSessionThenScheduleAndLogout() throws Exception {
        var login = mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content("{\"username\":\"demo-cg-a\",\"password\":\"Demo#2026\"}"))
            .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/caregivers/me").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Demo Caregiver A"));
        mvc.perform(get("/api/caregivers/me/schedule").session(session)
            .param("dateFrom",day.toString()).param("dateTo",day.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.upcomingVisits.length()").value(2))
            .andExpect(jsonPath("$.upcomingVisits[0].scheduledStart").value(day+"T09:00:00"))
            .andExpect(jsonPath("$.certificationAlerts.length()").value(2));
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/caregivers/me")).andExpect(status().isUnauthorized());
    }
    @Test void oldPlanAndMinimalElderAndTaskScopedEvidence() throws Exception {
        var result = mvc.perform(get("/api/visits/"+aVisit+"/work-pack").with(user("demo-cg-a").roles("CAREGIVER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.carePlanVersion").value(1))
            .andExpect(jsonPath("$.tasks.length()").value(3))
            .andExpect(jsonPath("$.requiredEvidenceKinds.length()").value(2))
            .andExpect(jsonPath("$.elder.medicalNotes").doesNotExist())
            .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("PRIVATE-DEMO","Version 2","PHOTO","Unassigned photo");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where resource_id=? and result='OK'",Long.class,aVisit)).isPositive();
    }
    @Test void historicalAssigneeCannotReadReassignedVisitAndDenialSurvivesRollback() throws Exception {
        mvc.perform(get("/api/visits/"+bVisit+"/work-pack").with(user("demo-cg-a").roles("CAREGIVER")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.elder").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where resource_id=? and result='DENIED'",Long.class,bVisit)).isPositive();
        mvc.perform(get("/api/visits/"+bVisit+"/work-pack").with(user("demo-cg-b").roles("CAREGIVER")))
            .andExpect(status().isOk());
        mvc.perform(get("/api/caregivers/me/schedule").with(user("demo-cg-b").roles("CAREGIVER"))
            .param("dateFrom",day.toString()).param("dateTo",day.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.upcomingVisits.length()").value(1));
    }
    @Test void authenticationRolesMissingAndInvalidDates() throws Exception {
        mvc.perform(get("/api/visits/"+aVisit+"/work-pack")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/caregivers/me").with(user("manager").roles("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/visits/9223372036854775807/work-pack").with(user("demo-cg-a").roles("CAREGIVER")))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/caregivers/me/schedule").with(user("demo-cg-a").roles("CAREGIVER"))
            .param("dateFrom",day.toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/caregivers/me/schedule").with(user("demo-cg-a").roles("CAREGIVER"))
            .param("dateFrom","invalid").param("dateTo",day.toString())).andExpect(status().isBadRequest());
    }
    @Test void emptyDayIsEmptyAndProfileMissingIs404() throws Exception {
        mvc.perform(get("/api/caregivers/me/schedule").with(user("demo-cg-a").roles("CAREGIVER"))
            .param("dateFrom",day.plusDays(7).toString()).param("dateTo",day.plusDays(7).toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.upcomingVisits").isEmpty());
        jdbc.update("insert ignore into app_user(username,password_hash,display_name) values('demo-cg-missing','unused','Missing')");
        jdbc.update("insert ignore into user_role(user_id,role) select id,'CAREGIVER' from app_user where username='demo-cg-missing'");
        mvc.perform(get("/api/caregivers/me").with(user("demo-cg-missing").roles("CAREGIVER")))
            .andExpect(status().isNotFound());
    }
    @Test void directSpaLinkForwardsToStaticEntryWithoutOpeningApi() throws Exception {
        mvc.perform(get("/caregiver/visits/"+aVisit)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }
}
