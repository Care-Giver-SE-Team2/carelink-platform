package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import sg.nus.carelink.testsupport.CaregiverHttpITSupport;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * SQL is used for independent state boundary fixtures; positive roster execution is tested in CG03.
 * The report itself is core's: visit hands it to core and keeps the command's receipt, so the tests
 * read the incident from the core stand-in and play the manager's part through it. Routing and
 * notifications are core's to test.
 */
class CaregiverIncidentWorkflowIT extends CaregiverHttpITSupport {
    private static final String REPORTS = "/api/caregivers/me/incidents";
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { SharedMySql.register(registry, CaregiverIncidentWorkflowIT.class, null); }
    private Map<String,Object> input(int version) {
        return new HashMap<>(Map.of("visitId",visit,"category","SERVICE","severity","MEDIUM","description","Observed facts only", "expectedVersion",version,"clientRequestId",UUID.randomUUID().toString()));
    }
    private void due() { jdbc.update("update visit set scheduled_start=?, scheduled_end=? where id=?",
            LocalDateTime.now(ZoneId.of("Asia/Singapore")).minusMinutes(5), LocalDateTime.now(ZoneId.of("Asia/Singapore")).plusHours(1),visit); }
    @Test void reportReachesCoreAndOwnSafeReadSurvivesReassignment() throws Exception {
        due();
        try (var a=browser(caregiverName); var b=browser(otherName); var relative=browser(familyName)) {
            var request=input(0);
            var created=body(a.post(REPORTS,request),201);
            long id=created.path("report").path("id").asLong();
            assertThat(created.path("report").path("respondBy").isNull()).isFalse();
            assertThat(jdbc.queryForObject("select status from visit where id=?",String.class,visit)).isEqualTo("EXCEPTION");
            assertThat(core.incidentsOf(visit)).singleElement().satisfies(raised -> {
                assertThat(raised.id()).isEqualTo(id);
                assertThat(raised.source()).isEqualTo("CAREGIVER");
                assertThat(raised.reportedByUserId()).isEqualTo(caregiverUser);
                assertThat(raised.description()).isEqualTo("Observed facts only");
            });
            core.resolve(id);
            assertThat(a.read("/api/caregivers/me/incidents/"+id).path("report").path("status").asString()).isEqualTo("RESOLVED");
            assertThat(jdbc.queryForObject("select status from visit where id=?",String.class,visit)).isEqualTo("EXCEPTION");
            // A replay is answered from visit's receipt; core is not asked again
            assertThat(body(a.post(REPORTS,request),200).path("replayed").asBoolean()).isTrue();
            assertThat(core.incidentsOf(visit)).hasSize(1);
            assertThat(a.read("/api/caregivers/me/incidents/"+id).toString()).doesNotContain("responderUserId","reportedByUserId","actorUserId");
            assertThat(b.get("/api/caregivers/me/incidents/"+id).statusCode()).isEqualTo(404);
            assertThat(relative.get("/api/caregivers/me/incidents/"+id).statusCode()).isEqualTo(403);
            jdbc.update("update visit set caregiver_id=?,version=version+1 where id=?",otherCaregiver,visit);
            assertThat(a.get("/api/visits/"+visit+"/work-pack").statusCode()).isEqualTo(403);
            assertThat(a.read("/api/caregivers/me/incidents/"+id).path("report").path("id").asLong()).isEqualTo(id);
            assertThat(a.post(REPORTS,input(2)).statusCode()).isEqualTo(403);
        }
    }
    @Test void invalidInputRolesCsrfAndFutureOrCancelledVisitHaveNoSuccessEffects() throws Exception {
        try(var a=browser(caregiverName);var b=browser(otherName);var mgr=browser(managerName)) {
            assertThat(a.post(REPORTS,input(0)).statusCode()).isEqualTo(409);
            assertThat(jdbc.queryForObject("select count(*) from visit_state_transition where visit_id=? and result='REJECTED'",Long.class,visit)).isEqualTo(1L);
            due();
            assertThat(b.post(REPORTS,input(0)).statusCode()).isEqualTo(403);
            assertThat(mgr.post(REPORTS,input(0)).statusCode()).isEqualTo(403);
            assertThat(a.withoutCsrf(REPORTS,input(0)).statusCode()).isEqualTo(403);
            var blank=input(0);blank.put("description"," ");assertThat(a.post(REPORTS,blank).statusCode()).isEqualTo(400);
            var invalid=input(0);invalid.put("category","DIAGNOSIS");assertThat(a.post(REPORTS,invalid).statusCode()).isEqualTo(400);
            assertThat(a.post(REPORTS,input(99)).statusCode()).isEqualTo(409);
            jdbc.update("update visit set status='CANCELLED' where id=?",visit);
            assertThat(a.post(REPORTS,input(0)).statusCode()).isEqualTo(409);
            assertThat(core.incidentsOf(visit)).isEmpty();
            assertThat(jdbc.queryForObject("select count(*) from caregiver_command_receipt where visit_id=?",Long.class,visit)).isZero();
            assertThat(a.get("/api/caregivers/me/incidents?size=51").statusCode()).isEqualTo(400);
        }
    }
    @Test void concurrentSameCommandProducesOneIncidentAndChangedPayloadConflicts() throws Exception {
        due();
        try(var a=browser(caregiverName);var second=browser(caregiverName)) {
            var request=input(0);
            var first=CompletableFuture.supplyAsync(() -> { try { return a.post(REPORTS,request); } catch(Exception e) { throw new RuntimeException(e); } });
            var next=second.post(REPORTS,request);
            assertThat(java.util.List.of(first.get().statusCode(),next.statusCode())).containsExactlyInAnyOrder(201,200);
            assertThat(core.incidentsOf(visit)).hasSize(1);
            request.put("description","Different facts"); assertThat(a.post(REPORTS,request).statusCode()).isEqualTo(409);
            body(a.post(REPORTS,input(1)),201);
            assertThat(jdbc.queryForObject("select count(*) from visit_state_transition where visit_id=? and result='APPLIED'",Long.class,visit)).isEqualTo(1L);
        }
    }
    @Test void completedVisitSupplementKeepsTerminalState() throws Exception {
        jdbc.update("update visit set status='COMPLETED',checked_out_at=? where id=?",LocalDateTime.now(),visit);
        try(var a=browser(caregiverName)) { body(a.post(REPORTS,input(0)),201); }
        assertThat(jdbc.queryForObject("select status from visit where id=?",String.class,visit)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from visit_state_transition where visit_id=? and result='APPLIED'",Long.class,visit)).isZero();
    }
}
