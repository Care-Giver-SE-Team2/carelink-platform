package sg.nus.carelink.rostering.controller;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import sg.nus.carelink.rostering.application.*;
import sg.nus.carelink.rostering.domain.model.CaregiverAvailability.Status;
import sg.nus.carelink.rostering.domain.model.CaregiverWorkSettings.*;
import sg.nus.carelink.rostering.domain.repository.CaregiverWorkSettingsStore;
import sg.nus.carelink.testsupport.SharedMySql;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"carelink.caregiver.expiry-scan-cron=-","carelink.missed-check-in.enabled=false","carelink.roster.schedule-cron=-","carelink.report.schedule-cron=-","carelink.caregiver.work-settings.service-codes=PERSONAL_CARE"})
@AutoConfigureMockMvc
@Import(CaregiverWorkSettingsIT.FixedClock.class)
class CaregiverWorkSettingsIT {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) { SharedMySql.register(r,CaregiverWorkSettingsIT.class,"+05:00"); }
    @TestConfiguration static class FixedClock { @Bean @Primary Clock clock() { return Clock.fixed(Instant.parse("2026-10-11T17:00:00Z"),ZoneOffset.UTC); } }
    private final LocalDate today=LocalDate.of(2026,10,12);
    @Autowired CaregiverWorkSettingsService service;
    @Autowired CaregiverWorkSettingsReader reader;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean sg.nus.carelink.rostering.domain.repository.VisitReassignment visits;
    @MockitoSpyBean CaregiverWorkSettingsStore store;
    @BeforeEach void clear() {
        reset(store);
        jdbc.update("delete from caregiver_work_settings_receipt"); jdbc.update("delete from caregiver_work_settings_state");
        jdbc.update("delete from caregiver_availability"); jdbc.update("delete from caregiver_preference");
        jdbc.update("delete from audit_log"); jdbc.update("delete from caregiver"); jdbc.update("delete from user_role"); jdbc.update("delete from app_user");
        for(int i=1;i<=2;i++) {
            jdbc.update("insert into app_user(id,username,password_hash,display_name,enabled) values (?,?,'{noop}test','Demo',true)",73000+i,"settings."+i);
            jdbc.update("insert into user_role(user_id,role) values (?,'CAREGIVER')",73000+i);
            jdbc.update("insert into caregiver(id,user_id,full_name,sector,status) values (?,?,'Demo','01',?)",73100+i,73000+i,i==1 ? "ONBOARDING" : "AVAILABLE");
        }
    }
    private List<Window> windows() { return List.of(new Window(LocalTime.of(9,0),LocalTime.of(12,0),Status.UNAVAILABLE)); }
    private String key() { return UUID.randomUUID().toString(); }
    @Test void pendingApplicantCanPrefillWithoutGainingWorkAccessAndSingaporeDateIsAuthoritative() throws Exception {
        var result=service.replaceDay("settings.1",today,windows(),0,key());
        assertThat(result.updatedAt()).isEqualTo(LocalDateTime.of(2026,10,12,1,0));
        assertThat(result.version()).isEqualTo(1); assertThat(result.consumptionStatus()).isEqualTo("NOT_APPLIED_TO_ROSTERING");
        mvc.perform(get("/api/caregivers/me/availability").param("dateFrom",today.toString()).param("dateTo",today.toString()).with(user("settings.1").roles("CAREGIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.settings.availability[0].windows[0].status").value("UNAVAILABLE"));
        // visit work permissions are verified by CaregiverAdmissionAccessIT in the owning visit service.
        assertThat(jdbc.queryForObject("select status from caregiver where id=73101",String.class)).isEqualTo("ONBOARDING");
    }
    @Test void replayReturnsOriginalVersionAfterLaterChangesAndDifferentPayloadConflicts() {
        String key=key(); var first=service.replaceDay("settings.1",today,windows(),0,key);
        service.replaceDay("settings.1",today,List.of(),1,key());
        assertThat(service.replaceDay("settings.1",today,windows(),0,key)).isEqualTo(first);
        assertThatThrownBy(()->service.replaceDay("settings.1",today.plusDays(1),windows(),0,key)).hasMessageContaining("different settings");
        var current=service.own("settings.1",today,today).settings();
        assertThat(current.availability()).isEmpty(); assertThat(current.uncoveredTimeMeaning()).isEqualTo("UNSPECIFIED");
    }
    @Test void preferencesAndAvailabilityShareOneVersionAndDoNotCreateVisits() {
        var p=new Preferences(List.of("PERSONAL_CARE"),List.of("01"),List.of(new WeeklyWindow(DayOfWeek.MONDAY,LocalTime.of(8,0),LocalTime.of(12,0))),4,new BigDecimal("4.5"));
        var saved=service.preferences("settings.1",p,0,key());
        assertThat(saved.preferences()).isEqualTo(p);
        assertThatThrownBy(()->service.replaceDay("settings.1",today,windows(),0,key())).hasMessageContaining("Settings changed");
        assertThat(jdbc.queryForObject("select preferred_sectors from caregiver_preference where caregiver_id=73101",String.class)).isEqualTo("01");
        verifyNoInteractions(visits);
        assertThat(jdbc.queryForObject("select count(*) from absence_report",Long.class)).isZero();
    }
    @Test void batchReaderPreservesMissingVersusUnspecifiedAndFlagsLegacyData() {
        jdbc.update("insert into caregiver_preference(caregiver_id,preferred_service_types,preferred_time_windows) values (73101,'UNKNOWN','invalid')");
        var result=reader.read(Set.of(73101L,73102L,99999L),today,today);
        assertThat(result).containsOnlyKeys(73101L,73102L);
        assertThat(result.get(73101L).preferences().serviceCodes()).containsExactly("UNKNOWN");
        assertThat(result.get(73101L).dataIssues()).contains("LEGACY_WEEKLY_WINDOW_INVALID","LEGACY_PREFERENCES_REQUIRE_REVIEW");
        assertThat(result.get(73102L).version()).isZero(); assertThat(result.get(73102L).availability()).isEmpty();
    }
    @Test void concurrentFirstWritesCommitOnlyOne() throws Exception {
        var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task=()->{ ready.countDown(); go.await(10,TimeUnit.SECONDS); try { service.replaceDay("settings.1",today,windows(),0,key()); return true; } catch(sg.nus.carelink.shared.error.BusinessRuleViolation _) { return false; } };
            var a=pool.submit(task); var b=pool.submit(task); assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("select count(*) from caregiver_availability",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from caregiver_work_settings_receipt",Long.class)).isEqualTo(1);
    }
    @Test void receiptFailureRollsBackDeletionNewWindowsAndVersion() {
        service.replaceDay("settings.1",today,windows(),0,key());
        doThrow(new IllegalStateException("forced receipt failure")).when(store).receipt(anyLong(),anyString(),anyString(),any());
        assertThatThrownBy(()->service.replaceDay("settings.1",today,List.of(),1,key())).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class);
        var current=service.own("settings.1",today,today).settings();
        assertThat(current.version()).isEqualTo(1); assertThat(current.availability().getFirst().windows()).isEqualTo(windows());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where resource_type='CAREGIVER_WORK_SETTINGS'",Long.class)).isEqualTo(1);
    }
    @Test void invalidDatesOverlapCodesAndInactiveOrDisabledAccountsCannotWrite() {
        assertThatThrownBy(()->service.replaceDay("settings.1",today.minusDays(1),windows(),0,key())).hasMessageContaining("90");
        assertThatThrownBy(()->service.replaceDay("settings.1",today,List.of(windows().getFirst(),windows().getFirst()),0,key())).hasMessageContaining("overlap");
        assertThatThrownBy(()->service.preferences("settings.1",new Preferences(List.of("unknown"),List.of(),List.of(),null,null),0,key())).hasMessageContaining("directory");
        jdbc.update("update caregiver set status='INACTIVE' where id=73101");
        assertThatThrownBy(()->service.replaceDay("settings.1",today,windows(),0,key())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        jdbc.update("update caregiver set status='ONBOARDING' where id=73101"); jdbc.update("update app_user set enabled=false where id=73001");
        assertThatThrownBy(()->service.replaceDay("settings.1",today,windows(),0,key())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(jdbc.queryForObject("select count(*) from caregiver_availability",Long.class)).isZero();
    }
    @Test void csrfAndSelfIdentityAreEnforced() throws Exception {
        String body="{\"windows\":[],\"expectedSettingsVersion\":0,\"clientRequestId\":\""+key()+"\"}";
        mvc.perform(put("/api/caregivers/me/availability/2026-10-12").with(user("settings.1").roles("CAREGIVER")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/caregivers/me/availability/2026-10-12").with(user("settings.1").roles("CAREGIVER")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body.replace("}",",\"caregiverId\":73102}"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/caregivers/me/work-settings")).andExpect(status().isUnauthorized());
        service.replaceDay("settings.1",today,windows(),0,key());
        assertThat(service.own("settings.2",today,today).settings().availability()).isEmpty();
    }
}
