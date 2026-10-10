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

/** SQL is used for independent state boundary fixtures; positive roster execution is tested in CG03. */
class CaregiverIncidentWorkflowIT extends CaregiverHttpITSupport {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { SharedMySql.register(registry, CaregiverIncidentWorkflowIT.class, null); }
    private Map<String,Object> input(int version) {
        return new HashMap<>(Map.of("visitId",visit,"category","SERVICE","severity","MEDIUM","description","Observed facts only", "expectedVersion",version,"clientRequestId",UUID.randomUUID().toString()));
    }
    private void due() { jdbc.update("update visit set scheduled_start=?, scheduled_end=? where id=?",
            LocalDateTime.now(ZoneId.of("Asia/Singapore")).minusMinutes(5), LocalDateTime.now(ZoneId.of("Asia/Singapore")).plusHours(1),visit); }
    @Test void reportRoutesToManagersAndOwnSafeReadSurvivesReassignment() throws Exception {
        due();
        try (var a=browser(caregiverName); var b=browser(otherName); var mgr=browser(managerName); var relative=browser(familyName)) {
            var request=input(0);
            var created=body(a.post("/api/incidents",request),201);
            long id=created.path("report").path("id").asLong();
            assertThat(created.path("report").path("respondBy").isNull()).isFalse();
            assertThat(jdbc.queryForObject("select status from visit where id=?",String.class,visit)).isEqualTo("EXCEPTION");
            assertThat(mgr.read("/api/incidents").toString()).contains("Observed facts only");
            body(mgr.post("/api/incidents/"+id+"/claim",Map.of()),200);
            body(mgr.post("/api/incidents/"+id+"/resolve",Map.of("resolutionNote","Manager checked and arranged follow-up","outcome","HANDLED_ON_SITE")),200);
            assertThat(a.read("/api/caregivers/me/incidents/"+id).path("report").path("status").asString()).isEqualTo("RESOLVED");
            assertThat(jdbc.queryForObject("select status from visit where id=?",String.class,visit)).isEqualTo("EXCEPTION");
            assertThat(jdbc.queryForObject("select count(*) from notification where resource_type='INCIDENT' and resource_id=? and recipient_user_id=?",Long.class,id,manager)).isGreaterThan(0);
            long notifications=jdbc.queryForObject("select count(*) from notification where resource_type='INCIDENT' and resource_id=?",Long.class,id);
            assertThat(body(a.post("/api/incidents",request),200).path("replayed").asBoolean()).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from notification where resource_type='INCIDENT' and resource_id=?",Long.class,id)).isEqualTo(notifications);
            assertThat(a.read("/api/caregivers/me/incidents/"+id).toString()).doesNotContain("responderUserId","reportedByUserId","actorUserId");
            assertThat(b.get("/api/caregivers/me/incidents/"+id).statusCode()).isEqualTo(404);
            assertThat(relative.get("/api/caregivers/me/incidents/"+id).statusCode()).isEqualTo(403);
            jdbc.update("update visit set caregiver_id=?,version=version+1 where id=?",otherCaregiver,visit);
            assertThat(a.get("/api/visits/"+visit+"/work-pack").statusCode()).isEqualTo(403);
            assertThat(a.read("/api/caregivers/me/incidents/"+id).path("report").path("id").asLong()).isEqualTo(id);
            assertThat(a.post("/api/incidents",input(2)).statusCode()).isEqualTo(403);
        }
    }
    @Test void invalidInputRolesCsrfAndFutureOrCancelledVisitHaveNoSuccessEffects() throws Exception {
        try(var a=browser(caregiverName);var b=browser(otherName);var mgr=browser(managerName)) {
            assertThat(a.post("/api/incidents",input(0)).statusCode()).isEqualTo(409);
            assertThat(jdbc.queryForObject("select count(*) from visit_state_transition where visit_id=? and result='REJECTED'",Long.class,visit)).isEqualTo(1L);
            due();
            assertThat(b.post("/api/incidents",input(0)).statusCode()).isEqualTo(403);
            assertThat(mgr.post("/api/incidents",input(0)).statusCode()).isEqualTo(403);
            assertThat(a.withoutCsrf("/api/incidents",input(0)).statusCode()).isEqualTo(403);
            var blank=input(0);blank.put("description"," ");assertThat(a.post("/api/incidents",blank).statusCode()).isEqualTo(400);
            var invalid=input(0);invalid.put("category","DIAGNOSIS");assertThat(a.post("/api/incidents",invalid).statusCode()).isEqualTo(400);
            assertThat(a.post("/api/incidents",input(99)).statusCode()).isEqualTo(409);
            jdbc.update("update visit set status='CANCELLED' where id=?",visit);
            assertThat(a.post("/api/incidents",input(0)).statusCode()).isEqualTo(409);
            assertThat(jdbc.queryForObject("select count(*) from incident where visit_id=?",Long.class,visit)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from caregiver_command_receipt where visit_id=?",Long.class,visit)).isZero();
            assertThat(a.get("/api/caregivers/me/incidents?size=51").statusCode()).isEqualTo(400);
        }
    }
    @Test void concurrentSameCommandProducesOneIncidentAndChangedPayloadConflicts() throws Exception {
        due();
        try(var a=browser(caregiverName);var second=browser(caregiverName)) {
            var request=input(0);
            var first=CompletableFuture.supplyAsync(() -> { try { return a.post("/api/incidents",request); } catch(Exception e) { throw new RuntimeException(e); } });
            var next=second.post("/api/incidents",request);
            assertThat(java.util.List.of(first.get().statusCode(),next.statusCode())).containsExactlyInAnyOrder(201,200);
            assertThat(jdbc.queryForObject("select count(*) from incident where visit_id=?",Long.class,visit)).isEqualTo(1L);
            request.put("description","Different facts"); assertThat(a.post("/api/incidents",request).statusCode()).isEqualTo(409);
            body(a.post("/api/incidents",input(1)),201);
            assertThat(jdbc.queryForObject("select count(*) from visit_state_transition where visit_id=? and result='APPLIED'",Long.class,visit)).isEqualTo(1L);
        }
    }
    @Test void completedVisitSupplementKeepsTerminalState() throws Exception {
        jdbc.update("update visit set status='COMPLETED',checked_out_at=? where id=?",LocalDateTime.now(),visit);
        try(var a=browser(caregiverName)) { body(a.post("/api/incidents",input(0)),201); }
        assertThat(jdbc.queryForObject("select status from visit where id=?",String.class,visit)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from visit_state_transition where visit_id=? and result='APPLIED'",Long.class,visit)).isZero();
    }
}
