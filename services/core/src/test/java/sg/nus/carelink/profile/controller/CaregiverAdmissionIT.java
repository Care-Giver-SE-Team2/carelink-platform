package sg.nus.carelink.profile.controller;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.profile.application.*;
import sg.nus.carelink.profile.domain.model.CaregiverApplication;
import sg.nus.carelink.profile.domain.repository.CaregiverAdmissionStore;
import sg.nus.carelink.testsupport.SharedMySql;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real accounts, MySQL decisions and core work authorization; visit permissions have separate service tests. */
@SpringBootTest(properties={"carelink.caregiver.expiry-scan-cron=-","carelink.missed-check-in.enabled=false","carelink.roster.schedule-cron=-","carelink.report.schedule-cron=-"})
@AutoConfigureMockMvc
@Import(CaregiverAdmissionIT.FixedClock.class)
class CaregiverAdmissionIT {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String SIGNUP="""
        {"username":"admission.a","password":"chosen-password","fullName":"Demo Caregiver","phone":"91234567"}
        """;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { SharedMySql.register(registry,CaregiverAdmissionIT.class,"+05:00"); }
    @TestConfiguration static class FixedClock {
        @Bean @Primary Clock clock() { return Clock.fixed(Instant.parse("2026-10-12T01:00:00Z"),ZoneOffset.UTC); }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CaregiverAdmissionService service;
    @Autowired CaregiverApplicationReader reader;
    @Autowired CaregiverAdmissionCommands commands;
    @MockitoSpyBean CaregiverAdmissionStore store;
    @BeforeEach void clear() {
        reset(store);
        jdbc.update("delete from caregiver_admission_receipt"); jdbc.update("delete from caregiver_application_history");
        jdbc.update("delete from caregiver_application"); jdbc.update("delete from caregiver_admission_state");
        jdbc.update("delete from audit_log"); jdbc.update("delete from caregiver"); jdbc.update("delete from user_role"); jdbc.update("delete from app_user");
        jdbc.update("insert into app_user(id,username,password_hash,display_name,enabled) values (72001,'admission.manager','{noop}test-password','Demo Manager',true)");
        jdbc.update("insert into user_role(user_id,role) values (72001,'MANAGER')");
    }
    long register() throws Exception {
        var response=mvc.perform(post("/api/caregiver-registrations").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.canWork").value(false))
                .andExpect(jsonPath("$.application.status").value("PENDING"))
                .andExpect(jsonPath("$.application.submittedAt").value("2026-10-12T09:00:00")).andReturn();
        return JSON.readTree(response.getResponse().getContentAsString()).path("application").path("id").asLong();
    }
    @Test void zeroCertificateApplicantAuthenticatesButCannotUseWorkEndpoints() throws Exception {
        long application=register();
        var login=mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admission.a\",\"password\":\"chosen-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("CAREGIVER")).andReturn();
        var session=(MockHttpSession)login.getRequest().getSession(false);
        mvc.perform(get("/api/caregivers/me/application").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.application.id").value(application));
        mvc.perform(get("/api/caregivers/me/absences").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/spot-checks").session(session)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from credential",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select password_hash from app_user where username='admission.a'",String.class)).isNotEqualTo("chosen-password");
    }
    @Test void managerApprovesWithoutCertificatesAndDoesNotCreateCredentials() throws Exception {
        long id=register(); String key=UUID.randomUUID().toString();
        var approved=commands.decide("admission.manager",id,true,null,0,key);
        assertThat(approved.canWork()).isTrue(); assertThat(approved.workStatus()).isEqualTo("AVAILABLE");
        assertThat(approved.version()).isEqualTo(1);
        assertThat(commands.decide("admission.manager",id,true,null,0,key)).isEqualTo(approved);
        assertThat(jdbc.queryForObject("select count(*) from credential",Long.class)).isZero();
        assertThat(reader.pendingApplications("admission.manager",0,10).total()).isZero();
    }
    @Test void rejectEditAndResubmitKeepOldDecisionAndRealSnapshot() throws Exception {
        long id=register(); commands.decide("admission.manager",id,false,"Correct your phone",0,UUID.randomUUID().toString());
        var details=new CaregiverApplication.Details("Corrected Name","+6581234567","English","North");
        service.edit("admission.a",details,1,UUID.randomUUID().toString());
        var result=service.resubmit("admission.a",2,UUID.randomUUID().toString());
        assertThat(result.application().roundNumber()).isEqualTo(2); assertThat(result.application().reviewReason()).isNull();
        assertThat(result.history()).hasSize(4);
        assertThat(result.history().get(1).snapshot().reviewReason()).isEqualTo("Correct your phone");
        assertThat(result.history().getFirst().snapshot().details().fullName()).isEqualTo("Demo Caregiver");
        assertThat(reader.applicationForManager("admission.manager",id).application().details()).isEqualTo(details);
    }
    @Test void staleDecisionAndChangedRequestKeyCannotApproveDifferentFacts() throws Exception {
        long id=register(); String key=UUID.randomUUID().toString();
        service.edit("admission.a",new CaregiverApplication.Details("New Name","+6591234567",null,null),0,key);
        assertThatThrownBy(() -> commands.decide("admission.manager",id,true,null,0,UUID.randomUUID().toString())).hasMessageContaining("changed");
        assertThatThrownBy(() -> service.edit("admission.a",new CaregiverApplication.Details("Other Name","+6591234567",null,null),0,key)).hasMessageContaining("different command");
        assertThat(service.own("admission.a").application().status()).isEqualTo(CaregiverApplication.Status.PENDING);
    }
    @Test void csrfUnknownRolesPasswordBytesAndUnauthenticatedAccessAreRejected() throws Exception {
        mvc.perform(post("/api/caregiver-registrations").contentType(MediaType.APPLICATION_JSON).content(SIGNUP)).andExpect(status().isForbidden());
        mvc.perform(post("/api/caregiver-registrations").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(SIGNUP.strip().replace("}",",\"role\":\"MANAGER\"}"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/caregiver-registrations").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(SIGNUP.replace("chosen-password","界".repeat(25)))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/caregivers/me/application")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from caregiver",Long.class)).isZero();
    }
    @Test void duplicateUsernameAndCrossAccountAccessDoNotLeakOrCreateProfiles() throws Exception {
        register();
        mvc.perform(post("/api/caregiver-registrations").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        mvc.perform(get("/api/caregivers/me/application").with(user("admission.manager").roles("MANAGER"))).andExpect(status().isForbidden());
        assertThatThrownBy(() -> reader.pendingApplications("admission.a",0,10)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(jdbc.queryForObject("select count(*) from caregiver",Long.class)).isEqualTo(1);
    }
    @Test void concurrentRegistrationHasOneAccountProfileAndApplication() throws Exception {
        var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> signup=()->{ ready.countDown(); assertThat(go.await(10,TimeUnit.SECONDS)).isTrue();
                return mvc.perform(post("/api/caregiver-registrations").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SIGNUP)).andReturn().getResponse().getStatus(); };
            var a=pool.submit(signup); var b=pool.submit(signup); assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(java.util.List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(jdbc.queryForObject("select count(*) from app_user where username='admission.a'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from caregiver",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from caregiver_application",Long.class)).isEqualTo(1);
    }
    @Test void saveFailureRollsBackNewAccountProfileAndApplication() throws Exception {
        doThrow(new IllegalStateException("forced history failure")).when(store).history(any(),anyLong(),eq("SUBMIT"),anyLong(),any());
        mvc.perform(post("/api/caregiver-registrations").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SIGNUP)).andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("select count(*) from app_user where username='admission.a'",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from caregiver",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from caregiver_application",Long.class)).isZero();
    }
    @Test void failedReceiptRollsBackDecisionAndActivation() throws Exception {
        long id=register();
        doThrow(new IllegalStateException("forced receipt failure")).when(store).receipt(anyLong(),anyString(),anyString(),any());
        assertThatThrownBy(() -> commands.decide("admission.manager",id,true,null,0,UUID.randomUUID().toString()))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasRootCauseMessage("forced receipt failure");
        var still=service.own("admission.a"); assertThat(still.workStatus()).isEqualTo("ONBOARDING");
        assertThat(still.version()).isZero(); assertThat(still.history()).hasSize(1);
        assertThat(still.application().status()).isEqualTo(CaregiverApplication.Status.PENDING);
    }
    @Test void twoConcurrentDecisionsCommitOnlyOne() throws Exception {
        long id=register(); var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> approve=() -> { ready.countDown(); go.await(10,TimeUnit.SECONDS); try { commands.decide("admission.manager",id,true,null,0,UUID.randomUUID().toString()); return true; } catch(sg.nus.carelink.shared.error.BusinessRuleViolation _) { return false; } };
            var first=pool.submit(approve); var second=pool.submit(approve); assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(java.util.List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(service.own("admission.a").history()).hasSize(2);
    }
    @Test void inactiveOrDisabledApplicantsCannotBeReactivated() throws Exception {
        long id=register(); jdbc.update("update caregiver set status='INACTIVE'");
        assertThatThrownBy(() -> commands.decide("admission.manager",id,true,null,0,UUID.randomUUID().toString())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        jdbc.update("update caregiver set status='ONBOARDING'"); jdbc.update("update app_user set enabled=false where username='admission.a'");
        assertThatThrownBy(() -> commands.decide("admission.manager",id,true,null,0,UUID.randomUUID().toString())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void legacyActiveContactUpdatesDoNotInventAdmissionOrAuthoritativeSector() throws Exception {
        jdbc.update("insert into app_user(id,username,password_hash,display_name,enabled) values (72002,'admission.legacy','{noop}test-password','Legacy',true)");
        jdbc.update("insert into user_role(user_id,role) values (72002,'CAREGIVER')");
        jdbc.update("insert into caregiver(user_id,full_name,sector,status) values (72002,'Legacy','TPY','BUSY')");
        assertThat(service.own("admission.legacy").compatibility()).isEqualTo("LEGACY_ACTIVE");
        var result=service.contact("admission.legacy","+6591234567",0,UUID.randomUUID().toString());
        assertThat(result.phone()).isEqualTo("+6591234567"); assertThat(result.workStatus()).isEqualTo("BUSY");
        assertThat(result.application()).isNull();
        assertThat(jdbc.queryForObject("select sector from caregiver",String.class)).isEqualTo("TPY");
    }
}
