package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import sg.nus.carelink.testsupport.SharedMySql;

/** Each change commits before the next request; no test-wide transaction or shared JPA cache. */
@SpringBootTest
@AutoConfigureMockMvc
class CaregiverScheduleChangesIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, CaregiverScheduleChangesIT.class, "+08:00");
    }
    private static final AtomicLong IDS = new AtomicLong(8000);
    private static final LocalDate DAY = LocalDate.of(2026,9,25);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    long id;
    String a;
    String b;

    @BeforeEach void seedIndependentScenario() {
        id = IDS.addAndGet(10);
        a = "change-a-" + id;
        b = "change-b-" + id;
        jdbc.update("insert into app_user(id,username,password_hash,display_name) values (?,?,'unused','A'),(?,?,'unused','B')",id,a,id+1,b);
        jdbc.update("insert into user_role(user_id,role) values (?,'CAREGIVER'),(?,'CAREGIVER')",id,id+1);
        jdbc.update("insert into caregiver(id,user_id,full_name,status) values (?,?,'A','AVAILABLE'),(?,?,'B','AVAILABLE')",id,id,id+1,id+1);
        jdbc.update("insert into elder(id,full_name,address) values (?,'Change demo elder','PRIVATE-CHANGE-ADDRESS')",id);
        jdbc.update("insert into care_plan(id,elder_id,version,status,published_at) values (?,?,1,'SUPERSEDED',now()),(?,?,2,'PUBLISHED',now())",id,id,id+1,id);
        jdbc.update("insert into care_plan_node(id,care_plan_id,name,evidence_type) values (?,?,'Assigned v1 task','CHECKLIST')",id,id);
        jdbc.update("insert into visit(id,elder_id,caregiver_id,care_plan_id,care_plan_node_id,service_type,scheduled_start,scheduled_end) values (?,?,?,?,?,'CHANGE_DEMO',?,?)",
                id,id,id,id,id,DAY.atTime(9,0),DAY.atTime(10,0));
        jdbc.update("insert into visit_task(visit_id,care_plan_node_id,name) values (?,?,'Assigned v1 task')",id,id);
        jdbc.update("insert into visit_assignment(visit_id,caregiver_id,status) values (?,?,'ACTIVE')",id,id);
    }

    private ResultActions schedule(String username, LocalDate day) throws Exception {
        return mvc.perform(get("/api/caregivers/me/schedule").with(user(username).roles("CAREGIVER"))
                .param("dateFrom",day.toString()).param("dateTo",day.toString()));
    }
    private ResultActions pack(String username) throws Exception {
        return mvc.perform(get("/api/visits/"+id+"/work-pack").with(user(username).roles("CAREGIVER")));
    }
    private void transfer(Long owner) {
        transaction.executeWithoutResult(ignored -> {
            jdbc.update("update visit_assignment set status='REPLACED',ended_at=now() where visit_id=? and status='ACTIVE'",id);
            jdbc.update("update visit set caregiver_id=?,version=version+1 where id=?",owner,id);
            if (owner != null) jdbc.update("insert into visit_assignment(visit_id,caregiver_id,status) values (?,?,'ACTIVE')",id,owner);
        });
    }

    @Test void committedReassignmentRemovesOldAccessAndPreservesBoundPlan() throws Exception {
        schedule(a,DAY).andExpect(jsonPath("$.upcomingVisits.length()").value(1));
        pack(a).andExpect(status().isOk()).andExpect(jsonPath("$.carePlanVersion").value(1));
        transfer(id+1);
        schedule(a,DAY).andExpect(jsonPath("$.upcomingVisits").isEmpty());
        var denied = pack(a).andExpect(status().isForbidden()).andReturn();
        assertThat(denied.getResponse().getContentAsString()).doesNotContain("PRIVATE-CHANGE-ADDRESS","Assigned v1 task");
        schedule(b,DAY).andExpect(jsonPath("$.upcomingVisits[0].id").value(id));
        pack(b).andExpect(status().isOk()).andExpect(jsonPath("$.carePlanVersion").value(1));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where resource_id=? and result='DENIED'",Long.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from visit_assignment where visit_id=? and caregiver_id=?",String.class,id,id)).isEqualTo("REPLACED");
    }

    @Test void unassignmentRevokesBothListAndDirectLink() throws Exception {
        pack(a).andExpect(status().isOk());
        transfer(null);
        schedule(a,DAY).andExpect(jsonPath("$.upcomingVisits").isEmpty());
        pack(a).andExpect(status().isForbidden());
        pack(b).andExpect(status().isForbidden());
    }

    @Test void cancelledSummaryRemainsButDetailsAreDeniedAndAudited() throws Exception {
        pack(a).andExpect(status().isOk());
        jdbc.update("update visit set status='CANCELLED',version=version+1 where id=?",id);
        var summary = schedule(a,DAY).andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingVisits[0].status").value("CANCELLED")).andReturn();
        assertThat(summary.getResponse().getContentAsString()).doesNotContain("PRIVATE-CHANGE-ADDRESS","Assigned v1 task");
        var denied = pack(a).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VISIT_CANCELLED")).andReturn();
        assertThat(denied.getResponse().getContentAsString()).doesNotContain("PRIVATE-CHANGE-ADDRESS","Assigned v1 task");
        pack(b).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from audit_log where resource_id=? and result='DENIED'",Long.class,id)).isEqualTo(2);
    }

    @Test void reschedulingMovesAcrossDateFiltersAndRetainsThePlanVersion() throws Exception {
        schedule(a,DAY).andExpect(jsonPath("$.upcomingVisits.length()").value(1));
        jdbc.update("update visit set scheduled_start=?,scheduled_end=?,version=version+1 where id=?",DAY.plusDays(1).atStartOfDay(),DAY.plusDays(1).atTime(1,0),id);
        schedule(a,DAY).andExpect(jsonPath("$.upcomingVisits").isEmpty());
        schedule(a,DAY.plusDays(1)).andExpect(jsonPath("$.upcomingVisits[0].scheduledStart").value("2026-09-26T00:00:00"));
        pack(a).andExpect(status().isOk()).andExpect(jsonPath("$.carePlanVersion").value(1))
                .andExpect(jsonPath("$.visit.scheduledStart").value("2026-09-26T00:00:00"));
    }

    @Test void newAssignmentsAndSameDayChangesAreOrderedByTimeThenId() throws Exception {
        schedule(b,DAY).andExpect(jsonPath("$.upcomingVisits").isEmpty());
        transfer(id+1);
        jdbc.update("insert into visit(id,elder_id,caregiver_id,service_type,scheduled_start) values (?,?,?,'SECOND',?)",id+1,id,id+1,DAY.atTime(8,0));
        schedule(b,DAY).andExpect(jsonPath("$.upcomingVisits[0].id").value(id+1));
        jdbc.update("update visit set scheduled_start=?,version=version+1 where id=?",DAY.atTime(8,0),id);
        schedule(b,DAY).andExpect(jsonPath("$.upcomingVisits[0].id").value(id)).andExpect(jsonPath("$.upcomingVisits[1].id").value(id+1));
        jdbc.update("update visit set scheduled_start=? where id=?",DAY.minusDays(1).atTime(23,59),id);
        schedule(b,DAY).andExpect(jsonPath("$.upcomingVisits.length()").value(1));
    }

    @ParameterizedTest @ValueSource(strings={"COMPLETED","VERIFIED","AUTO_CLOSED","EXCEPTION"})
    void otherStatesRetainExistingReadOnlyAccess(String state) throws Exception {
        jdbc.update("update visit set status=? where id=?",state,id);
        pack(a).andExpect(status().isOk()).andExpect(jsonPath("$.visit.status").value(state));
    }

    @Test void demoSeedIsRepeatableAndDoesNotOverwriteExistingVisits() throws Exception {
        jdbc.update("insert into app_user(username,password_hash,display_name) values ('demo-cg-a','unused','Demo A'),('demo-cg-b','unused','Demo B')");
        jdbc.update("insert into user_role(user_id,role) select id,'CAREGIVER' from app_user where username in ('demo-cg-a','demo-cg-b')");
        jdbc.update("insert into caregiver(user_id,full_name,status) select id,display_name,'AVAILABLE' from app_user where username in ('demo-cg-a','demo-cg-b')");
        var before = jdbc.queryForMap("select * from visit where id=?",id);
        loadDemo();
        var first = jdbc.queryForList("select * from visit where service_type in ('DEMO3_CHANGE','DEMO3_CONTROL') order by id");
        assertThat(first).hasSize(2);
        loadDemo();
        assertThat(jdbc.queryForList("select * from visit where service_type in ('DEMO3_CHANGE','DEMO3_CONTROL') order by id")).isEqualTo(first);
        assertThat(jdbc.queryForMap("select * from visit where id=?",id)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from visit_task t join visit v on v.id=t.visit_id where v.service_type in ('DEMO3_CHANGE','DEMO3_CONTROL')",Long.class)).isEqualTo(2);
        long demoVisit = jdbc.queryForObject("select id from visit where service_type='DEMO3_CHANGE'",Long.class);
        mvc.perform(get("/api/visits/"+demoVisit+"/work-pack").with(user("demo-cg-a").roles("CAREGIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.carePlanVersion").value(1))
                .andExpect(jsonPath("$.tasks.length()").value(1));
    }

    private void loadDemo() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection,new EncodedResource(new ClassPathResource("db/demo/caregiver-slice3.sql"),StandardCharsets.UTF_8));
            return null;
        });
    }
}
