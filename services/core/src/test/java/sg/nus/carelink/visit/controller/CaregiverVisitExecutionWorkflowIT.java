package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import sg.nus.carelink.testsupport.CaregiverHttpITSupport;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.visit.domain.repository.CaregiverCommandStore;

/** Every positive execution begins with manager HTTP plan publication and assignment. */
class CaregiverVisitExecutionWorkflowIT extends CaregiverHttpITSupport {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { SharedMySql.register(registry,CaregiverVisitExecutionWorkflowIT.class,"+05:00"); }
    @MockitoSpyBean CaregiverCommandStore receipts;
    private long plannedVisit() throws Exception {
        var start=LocalDateTime.now(ZoneId.of("Asia/Singapore")).plusMinutes(5).withSecond(0).withNano(0);
        try(var mgr=browser(managerName)) {
            body(mgr.put("/api/elders/"+elder+"/primary-caregiver",Map.of("caregiverId",caregiver)),200);
            long plan=body(mgr.post("/api/care-plans",Map.of("elderId",elder)),201).path("id").asLong();
            body(mgr.post("/api/care-plans/"+plan+"/publish",Map.of("startDate",start.toLocalDate().toString(),"nodes",List.of(Map.of(
                    "groupName","Personal care","name","Care routine","evidenceType","CHECKLIST","visits",List.of(Map.of("day",start.getDayOfWeek().name(),"startTime",start.toLocalTime().toString(),"minutes",60)))))),200);
            return jdbc.queryForObject("select id from visit where care_plan_id=? and scheduled_start=?",Long.class,plan,start);
        }
    }
    private Map<String,Object> check(int version) { return new HashMap<>(Map.of("expectedVersion",version,"clientRequestId",UUID.randomUUID().toString(),"locationSource","MANUAL_LOCATION_NOTE","locationNote","At the service doorway")); }
    private Map<String,Object> task(int version,String status) { return new HashMap<>(Map.of("expectedVersion",version,"clientRequestId",UUID.randomUUID().toString(),"status",status,"outcome","Observed result","caregiverNote","Internal factual reason")); }
    @Test void realRosterCheckInTaskIncidentAndFamilyReadsShareOneSourceOfTruth() throws Exception {
        long id=plannedVisit();
        try(var a=browser(caregiverName);var familyBrowser=browser(familyName);var mgr=browser(managerName)) {
            assertThat(jdbc.queryForObject("select count(*) from visit_task where visit_id=?",Long.class,id)).isZero();
            assertThat(a.read("/api/visits/"+id+"/work-pack").path("execution").path("allowedActions").toString()).contains("CHECK_IN");
            var input=check(0);body(a.post("/api/visits/"+id+"/check-in",input),200);
            assertThat(body(a.post("/api/visits/"+id+"/check-in",input),200).path("replayed").asBoolean()).isTrue();
            assertThat(a.post("/api/visits/"+id+"/check-in",check(1)).statusCode()).isEqualTo(409);
            var pack=a.read("/api/visits/"+id+"/work-pack");long taskId=pack.path("tasks").get(0).path("id").asLong();
            assertThat(pack.path("visit").path("version").asInt()).isEqualTo(1);
            assertThat(pack.path("execution").path("locationSource").asString()).isEqualTo("MANUAL_LOCATION_NOTE");
            assertThat(pack.path("requiredEvidenceKinds").toString()).contains("CHECKLIST");
            var familyDetail=familyBrowser.read("/api/visits/"+id);
            assertThat(familyDetail.toString()).contains("IN_PROGRESS","checkedInAt").doesNotContain("locationNote","latitude","longitude");
            var timeline=familyBrowser.read("/api/visits/"+id+"/timeline");assertThat(timeline.size()).isEqualTo(2);
            assertThat(timeline.toString()).doesNotContain("REJECTED","actorUserId","rejectionReason");
            var result=task(1,"DONE");body(a.post("/api/visits/"+id+"/tasks/"+taskId+"/complete",result),200);
            body(a.post("/api/visits/"+id+"/tasks/"+taskId+"/complete",result),200);
            assertThat(a.post("/api/visits/"+id+"/tasks/"+taskId+"/complete",task(2,"SKIPPED")).statusCode()).isEqualTo(409);
            assertThat(a.read("/api/visits/"+id+"/tasks").toString()).contains("Internal factual reason");
            var familyTasks=familyBrowser.read("/api/visits/"+id+"/tasks");assertThat(familyTasks.get(0).path("status").asString()).isEqualTo("DONE");
            assertThat(familyTasks.get(0).path("completedAt").isNull()).isFalse();assertThat(familyTasks.toString()).doesNotContain("caregiverNote","outcome","Internal factual reason");
            assertThat(familyBrowser.read("/api/visits/"+id).toString()).contains("IN_PROGRESS");
            var report=body(a.post("/api/incidents",Map.of("visitId",id,"expectedVersion",2,"clientRequestId",UUID.randomUUID().toString(),"category","SERVICE","severity","LOW","description","Observed a service issue")),201);
            assertThat(mgr.read("/api/incidents/"+report.path("report").path("id").asLong()).toString()).contains("Observed a service issue");
            assertThat(a.read("/api/visits/"+id+"/work-pack").path("visit").path("status").asString()).isEqualTo("EXCEPTION");
            assertThat(a.post("/api/visits/"+id+"/tasks/"+taskId+"/complete",task(3,"DONE")).statusCode()).isEqualTo(409);
            assertThat(familyBrowser.read("/api/visits/"+id+"/timeline").size()).isEqualTo(3);
            assertThat(jdbc.queryForObject("select count(*) from notification where resource_type='VISIT' and resource_id=? and event_type='VISIT_COMPLETED'",Long.class,id)).isZero();
        }
    }
    @Test void skippedAndRefusedRequireReasonsAndNeverCountAsDone() throws Exception {
        for(String status:List.of("SKIPPED","REFUSED")) {
            long id=plannedVisit();
            try(var a=browser(caregiverName);var relative=browser(familyName)) {
                body(a.post("/api/visits/"+id+"/check-in",check(0)),200);
                long taskId=a.read("/api/visits/"+id+"/work-pack").path("tasks").get(0).path("id").asLong();
                var missing=task(1,status);missing.put("caregiverNote"," ");assertThat(a.post("/api/visits/"+id+"/tasks/"+taskId+"/complete",missing).statusCode()).isEqualTo(400);
                body(a.post("/api/visits/"+id+"/tasks/"+taskId+"/complete",task(1,status)),200);
                var row=relative.read("/api/visits/"+id+"/tasks").get(0);assertThat(row.path("status").asString()).isEqualTo(status);assertThat(row.path("completedAt").isNull()).isTrue();
                assertThat(jdbc.queryForObject("select status from visit where id=?",String.class,id)).isEqualTo("IN_PROGRESS");
            }
        }
    }
    @Test void gpsValidationRolesCsrfPlanMismatchAndTaskParentAreChecked() throws Exception {
        long id=plannedVisit();
        try(var a=browser(caregiverName);var b=browser(otherName);var relative=browser(familyName)) {
            var gps=new HashMap<>(Map.<String,Object>of("expectedVersion",0,"clientRequestId",UUID.randomUUID().toString(),"locationSource","GPS","latitude",91,"longitude",103.8,"accuracy",5,"clientCapturedAt",Instant.now().toString()));
            assertThat(a.post("/api/visits/"+id+"/check-in",gps).statusCode()).isEqualTo(400);
            assertThat(a.withoutCsrf("/api/visits/"+id+"/check-in",check(0)).statusCode()).isEqualTo(403);
            assertThat(b.post("/api/visits/"+id+"/check-in",check(0)).statusCode()).isEqualTo(403);
            assertThat(relative.post("/api/visits/"+id+"/check-in",check(0)).statusCode()).isEqualTo(403);
            gps.put("latitude",1.3);body(a.post("/api/visits/"+id+"/check-in",gps),200);
            assertThat(jdbc.queryForObject("select location_source from visit_check_in_record where visit_id=?",String.class,id)).isEqualTo("GPS");
            long wrong=insert("insert into visit_task(visit_id,name,status) values (?,'Other visit task','PENDING')",visit);
            assertThat(a.post("/api/visits/"+id+"/tasks/"+wrong+"/complete",task(1,"DONE")).statusCode()).isEqualTo(404);
            assertThat(b.get("/api/visits/"+id+"/tasks").statusCode()).isEqualTo(403);
            assertThat(a.post("/api/visits/"+visit+"/check-in",check(0)).statusCode()).isEqualTo(409);
        }
    }
    @Test void concurrentCheckInsAndTaskVersusIncidentHaveOnlyOneSuccessfulWriter() throws Exception {
        long id=plannedVisit();
        try(var a=browser(caregiverName);var second=browser(caregiverName)) {
            var request=check(0);
            var first=CompletableFuture.supplyAsync(()->{try{return a.post("/api/visits/"+id+"/check-in",request);}catch(Exception e){throw new RuntimeException(e);}});
            var next=second.post("/api/visits/"+id+"/check-in",request);assertThat(List.of(first.get().statusCode(),next.statusCode())).containsExactly(200,200);
            assertThat(jdbc.queryForObject("select count(*) from visit_task where visit_id=?",Long.class,id)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from visit_check_in_record where visit_id=?",Long.class,id)).isEqualTo(1L);
            long taskId=a.read("/api/visits/"+id+"/work-pack").path("tasks").get(0).path("id").asLong();
            var write=CompletableFuture.supplyAsync(()->{try{return a.post("/api/visits/"+id+"/tasks/"+taskId+"/complete",task(1,"DONE"));}catch(Exception e){throw new RuntimeException(e);}});
            var incident=second.post("/api/incidents",Map.of("visitId",id,"expectedVersion",1,"clientRequestId",UUID.randomUUID().toString(),"category","SERVICE","severity","LOW","description","Observed issue"));
            int taskStatus=write.get().statusCode();assertThat(List.of(taskStatus,incident.statusCode())).contains(409);
            assertThat((taskStatus==200) ^ (incident.statusCode()==201)).isTrue();
            assertThat(jdbc.queryForObject("select version from visit where id=?",Integer.class,id)).isEqualTo(2);
        }
    }
    @Test void receiptFailureRollsBackCheckInTaskInitializationAndAppliedEvents() throws Exception {
        long id=plannedVisit();doThrow(new IllegalStateException("simulated receipt fault")).when(receipts).save(any());
        try(var a=browser(caregiverName)) { assertThat(a.post("/api/visits/"+id+"/check-in",check(0)).statusCode()).isEqualTo(503); }
        assertThat(jdbc.queryForObject("select status from visit where id=?",String.class,id)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("select count(*) from visit_check_in_record where visit_id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from visit_task where visit_id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from visit_state_transition where visit_id=? and result='APPLIED'",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from caregiver_command_receipt where visit_id=?",Long.class,id)).isZero();
    }
    @Test void receiptFailureRollsBackIncidentRoutingNotificationsAndVisitPause() throws Exception {
        long id=plannedVisit();
        try(var a=browser(caregiverName)) {
            body(a.post("/api/visits/"+id+"/check-in",check(0)),200);
            long notifications=jdbc.queryForObject("select count(*) from notification",Long.class);
            doThrow(new IllegalStateException("simulated receipt fault")).when(receipts).save(any());
            assertThat(a.post("/api/incidents",Map.of("visitId",id,"expectedVersion",1,"clientRequestId",UUID.randomUUID().toString(),
                    "category","SERVICE","severity","LOW","description","Atomic rollback fixture")).statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("select count(*) from notification",Long.class)).isEqualTo(notifications);
        }
        assertThat(jdbc.queryForObject("select status from visit where id=?",String.class,id)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("select version from visit where id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from incident where visit_id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from visit_state_transition where visit_id=? and result='APPLIED'",Long.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from caregiver_command_receipt where visit_id=?",Long.class,id)).isEqualTo(1);
    }
}
