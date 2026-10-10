package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import sg.nus.carelink.testsupport.MissedCheckInITSupport;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.visit.domain.repository.CaregiverCommandStore;

/** Positive completion is always produced by real caregiver HTTP commands, never SQL. */
class CaregiverCheckOutWorkflowIT extends MissedCheckInITSupport {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry,CaregiverCheckOutWorkflowIT.class,"+05:00","connectionTimeZone=Asia/Singapore");
    }
    @MockitoSpyBean CaregiverCommandStore receipts;
    private Map<String,Object> departure(int version) {
        return Map.of("expectedVersion",version,"clientRequestId",UUID.randomUUID().toString());
    }
    private String path(long id) { return "/api/visits/"+id+"/check-out"; }
    private Map<String,Object> report(long id,int version) {
        return Map.of("visitId",id,"expectedVersion",version,"clientRequestId",UUID.randomUUID().toString(),
                "category","SERVICE","severity","LOW","description","Synthetic service observation");
    }
    @Test void pendingTasksMissingEvidenceAndNoVitalsDoNotRequireApprovalAndTimesPersistOnRetry() throws Exception {
        long id=plannedVisit();overdue();assertThat(scan.trigger(id)).isTrue();long event=incident(id);
        try(var cg=browser(caregiverName);var familyBrowser=browser(familyName)) {
            body(cg.post("/api/visits/"+id+"/check-in",check(1)),200);
            // Planned end is 11:05; check-out at 12:00 must not use the arrival window.
            clock.at(START.plusSeconds(7200).plusNanos(987654321));
            var request=departure(2);var saved=body(cg.post(path(id),request),200);
            assertThat(saved.path("savedState").asString()).isEqualTo("COMPLETED");
            assertThat(saved.path("checkedInAt").asString()).isEqualTo("2026-10-08T10:15:01");
            assertThat(saved.path("checkedOutAt").asString()).isEqualTo("2026-10-08T12:00:00");
            clock.at(START.plusSeconds(7500));var retry=body(cg.post(path(id),request),200);
            assertThat(retry.path("checkedOutAt")).isEqualTo(saved.path("checkedOutAt"));
            assertThat(retry.path("visitVersion")).isEqualTo(saved.path("visitVersion"));
            assertThat(retry.path("replayed").asBoolean()).isTrue();
            assertThat(cg.post(path(id),departure(3)).statusCode()).isEqualTo(409);
            assertThat(cg.post(path(id),Map.of("expectedVersion",3,"clientRequestId",request.get("clientRequestId"))).statusCode()).isEqualTo(409);
            var pack=cg.read("/api/visits/"+id+"/work-pack");
            assertThat(pack.path("execution").path("checkedOutAt")).isEqualTo(saved.path("checkedOutAt"));
            assertThat(pack.path("execution").path("allowedActions").toString()).doesNotContain("CHECK_OUT","TASK_RESULT","HEALTH_RECORD");
            assertThat(pack.path("tasks").get(0).path("status").asString()).isEqualTo("PENDING");
            long taskId=pack.path("tasks").get(0).path("id").asLong();
            assertThat(cg.post("/api/visits/"+id+"/tasks/"+taskId+"/complete",Map.of("expectedVersion",3,"clientRequestId",UUID.randomUUID().toString(),"status","DONE")).statusCode()).isEqualTo(409);
            assertThat(familyBrowser.read("/api/visits/"+id).path("status").asString()).isEqualTo("COMPLETED");
            assertThat(familyBrowser.read("/api/visits/"+id+"/timeline").size()).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT status FROM incident WHERE id=?",String.class,event)).isEqualTo("OPEN");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE incident_id=?",Long.class,event)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_state_transition WHERE visit_id=? AND to_state='COMPLETED' AND result='APPLIED'",Long.class,id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_evidence WHERE visit_id=?",Long.class,id)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vital_sign WHERE visit_id=?",Long.class,id)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder_confirmation WHERE visit_id=?",Long.class,id)).isZero();
            assertThat(version(id)).isEqualTo(3);
        }
        try(var fresh=browser(caregiverName)) {
            assertThat(fresh.read("/api/visits/"+id+"/work-pack").path("execution").path("checkedOutAt").asString()).isEqualTo("2026-10-08T12:00:00");
        }
    }
    @Test void permissionsCsrfValidationKeyReuseAndUnstartedVisitAreRejected() throws Exception {
        long id=plannedVisit();
        try(var cg=browser(caregiverName);var other=browser(otherName);var familyBrowser=browser(familyName)) {
            assertThat(cg.post(path(id),departure(0)).statusCode()).isEqualTo(409);
            assertThat(other.post(path(id),departure(0)).statusCode()).isEqualTo(403);
            assertThat(familyBrowser.post(path(id),departure(0)).statusCode()).isEqualTo(403);
            assertThat(cg.withoutCsrf(path(id),departure(0)).statusCode()).isEqualTo(403);
            assertThat(cg.post(path(id),Map.of("expectedVersion",-1,"clientRequestId","not-a-uuid")).statusCode()).isEqualTo(400);
            assertThat(cg.post(path(id),Map.of("expectedVersion",0)).statusCode()).isEqualTo(400);
            var arrival=check(0);body(cg.post("/api/visits/"+id+"/check-in",arrival),200);
            assertThat(cg.post(path(id),Map.of("expectedVersion",1,"clientRequestId",arrival.get("clientRequestId"))).statusCode()).isEqualTo(409);
            body(cg.post(path(id),departure(1)),200); // Equal arrival/departure time is legal.
        }
    }
    @ParameterizedTest @ValueSource(ints={123456000,987654000})
    void fractionalArrivalNeverRoundsIntoTheFutureOrRequiresAnExtraSecond(int nanos) throws Exception {
        long id=plannedVisit();clock.at(START.plusSeconds(300).plusNanos(nanos));
        try(var cg=browser(caregiverName)) {
            body(cg.post("/api/visits/"+id+"/check-in",check(0)),200);
            var pack=cg.read("/api/visits/"+id+"/work-pack");
            assertThat(pack.path("execution").path("checkedInAt").asString()).isEqualTo("2026-10-08T10:05:00");
            assertThat(pack.path("execution").path("allowedActions").toString()).contains("CHECK_OUT");
            var completed=body(cg.post(path(id),departure(1)),200);
            assertThat(completed.path("checkedOutAt")).isEqualTo(completed.path("checkedInAt"));
        }
    }
    @Test void checkoutBeforeArrivalClockIsRejectedAndLatestCheckInWindowIsUnchanged() throws Exception {
        long id=plannedVisit();clock.at(START.plusSeconds(300));
        try(var cg=browser(caregiverName)) {
            body(cg.post("/api/visits/"+id+"/check-in",check(0)),200);
            clock.at(START.plusSeconds(299));
            assertThat(cg.post(path(id),departure(1)).statusCode()).isEqualTo(409);
            assertThat(version(id)).isEqualTo(1);
        }
        clock.at(START);long late=plannedVisit();clock.at(START.plusSeconds(3901));
        try(var cg=browser(caregiverName)) {
            assertThat(cg.post("/api/visits/"+late+"/check-in",check(0)).statusCode()).isEqualTo(409);
        }
    }
    @Test void checkoutKeepsHealthBatchAndPostCompletionReportsDoNotRevertState() throws Exception {
        long id=plannedVisit();
        try(var cg=browser(caregiverName)) {
            body(cg.post("/api/visits/"+id+"/check-in",check(0)),200);
            var health=Map.of("expectedVersion",1,"clientRequestId",UUID.randomUUID().toString(),"systolic",122,"diastolic",81,
                    "pulse",72,"temperature",36.6,"healthFlag","ATTENTION","healthNote","Synthetic observation");
            body(cg.post("/api/visits/"+id+"/health-records",health),201);
            body(cg.post(path(id),departure(2)),200);
            assertThat(jdbc.queryForObject("SELECT health_note FROM visit WHERE id=?",String.class,id)).isEqualTo("Synthetic observation");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vital_sign WHERE visit_id=?",Long.class,id)).isEqualTo(4);
            assertThat(cg.post("/api/visits/"+id+"/health-records",new HashMap<>(Map.of("expectedVersion",3,"clientRequestId",UUID.randomUUID().toString(),"healthFlag","NO_CONCERN","healthNote","Not measured"))).statusCode()).isEqualTo(409);
            body(cg.post("/api/incidents",report(id,3)),201);
            assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("COMPLETED");
        }
    }
    @Test void operationalIncidentFirstStillBlocksOrdinaryCheckout() throws Exception {
        long id=plannedVisit();clock.at(START.plusSeconds(300));
        try(var cg=browser(caregiverName)) {
            body(cg.post("/api/visits/"+id+"/check-in",check(0)),200);
            body(cg.post("/api/incidents",report(id,1)),201);
            assertThat(cg.post(path(id),departure(2)).statusCode()).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT checked_out_at FROM visit WHERE id=?",String.class,id)).isNull();
        }
    }
    /** Whether a filed report counts the visit is report's to test, now that report runs on its own. */
    @Test void actualDepartureFeedsTheEldersPendingConfirmationsWithoutPreApproval() throws Exception {
        long id=plannedVisit();String elderName="checkout-elder-"+UUID.randomUUID();
        long elderUser=insert("INSERT INTO app_user(username,password_hash,display_name) VALUES (?,'{noop}test-password','Fictional elder')",elderName);
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,'ELDER')",elderUser);
        jdbc.update("UPDATE elder SET user_id=? WHERE id=?",elderUser,elder);
        try(var cg=browser(caregiverName);var elderBrowser=browser(elderName)) {
            body(cg.post("/api/visits/"+id+"/check-in",check(0)),200);
            clock.at(START.plusSeconds(3900));body(cg.post(path(id),departure(1)),200);
            assertThat(elderBrowser.read("/api/elders/me/visits/awaiting-confirmation").toString()).contains("\"visitId\":"+id,"COMPLETED","checkedOutAt");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM elder_confirmation WHERE visit_id=?",Long.class,id)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("COMPLETED");
        }
    }
    @Test void receiptFailureRollsBackDepartureStateTimeHistoryAndVersion() throws Exception {
        long id=plannedVisit();
        try(var cg=browser(caregiverName)) {
            body(cg.post("/api/visits/"+id+"/check-in",check(0)),200);
            doThrow(new IllegalStateException("Synthetic receipt failure")).when(receipts).save(any());
            assertThat(cg.post(path(id),departure(1)).statusCode()).isEqualTo(503);
        }
        assertThat(version(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT checked_out_at FROM visit WHERE id=?",String.class,id)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_state_transition WHERE visit_id=? AND to_state='COMPLETED' AND result='APPLIED'",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM caregiver_command_receipt WHERE visit_id=? AND action_code='CHECK_OUT'",Long.class,id)).isZero();
    }
    /** This intentionally simulates the old committed SYS03 format, never a new positive service completion. */
    private void legacyPause(long id) {
        legacyPause(id,0);
    }
    private void legacyPause(long id,int nanos) {
        clock.at(START.plusSeconds(901).plusNanos(nanos));scan.trigger(id);
        var at=Timestamp.valueOf(LocalDateTime.ofInstant(clock.instant(),SGT));
        jdbc.update("UPDATE visit SET status='EXCEPTION' WHERE id=?",id);
        jdbc.update("INSERT INTO visit_state_transition(visit_id,from_state,to_state,result,occurred_at) VALUES (?,'SCHEDULED','EXCEPTION','APPLIED',?)",id,at);
    }
    @ParameterizedTest @ValueSource(ints={0,123456000,987654000})
    void provenLegacyPauseResumesOnlyDuringCaregiverCheckInAndPreservesOriginalAlert(int nanos) throws Exception {
        long id=plannedVisit();legacyPause(id,nanos);long event=incident(id);
        try(var cg=browser(caregiverName)) {
            assertThat(cg.read("/api/visits/"+id+"/work-pack").path("execution").path("allowedActions").toString()).contains("CHECK_IN");
            assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("EXCEPTION");
            var request=check(1);body(cg.post("/api/visits/"+id+"/check-in",request),200);
            body(cg.post("/api/visits/"+id+"/check-in",request),200);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_state_transition WHERE visit_id=? AND from_state='EXCEPTION' AND to_state='ARRIVED' AND result='APPLIED'",Long.class,id)).isEqualTo(1);
            assertThat(incident(id)).isEqualTo(event);assertThat(count("incident",id)).isEqualTo(1);
            clock.at(START.plusSeconds(905));body(cg.post(path(id),departure(2)),200);
        }
    }
    @ParameterizedTest @ValueSource(strings={"OTHER_INCIDENT","VERSION","ASSIGNMENT","ABSENCE","MISSING_HISTORY","NO_LEDGER"})
    void ambiguousLegacyPauseNeverOpensOtherExceptions(String reason) throws Exception {
        long id=plannedVisit();legacyPause(id);
        switch(reason) {
            case "OTHER_INCIDENT" -> jdbc.update("INSERT INTO incident(elder_id,visit_id,source,category,severity,status,description) VALUES (?,?,'CAREGIVER','SERVICE','LOW','RESOLVED','Synthetic independent pause')",elder,id);
            case "VERSION" -> jdbc.update("UPDATE visit SET version=version+1 WHERE id=?",id);
            case "ASSIGNMENT" -> jdbc.update("UPDATE visit_missed_check_in_trigger SET triggered_caregiver_id=? WHERE visit_id=?",otherCaregiver,id);
            case "ABSENCE" -> {
                long absence=insert("INSERT INTO absence_report(caregiver_id,start_date,end_date,reason) VALUES (?,'2026-10-08','2026-10-09','Synthetic absence')",caregiver);
                jdbc.update("UPDATE visit SET absence_id=? WHERE id=?",absence,id);
            }
            case "MISSING_HISTORY" -> jdbc.update("DELETE FROM visit_state_transition WHERE visit_id=?",id);
            case "NO_LEDGER" -> jdbc.update("DELETE FROM visit_missed_check_in_trigger WHERE visit_id=?",id);
            default -> throw new IllegalArgumentException(reason);
        }
        try(var cg=browser(caregiverName)) {
            assertThat(cg.read("/api/visits/"+id+"/work-pack").path("execution").path("allowedActions").toString()).doesNotContain("CHECK_IN","CHECK_OUT");
            assertThat(cg.post("/api/visits/"+id+"/check-in",check(version(id))).statusCode()).isEqualTo(409);
        }
        assertThat(count("visit_check_in_record",id)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("EXCEPTION");
    }
}
