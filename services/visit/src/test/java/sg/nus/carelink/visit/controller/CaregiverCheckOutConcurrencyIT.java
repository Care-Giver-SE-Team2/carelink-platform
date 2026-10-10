package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import sg.nus.carelink.testsupport.MissedCheckInITSupport;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.visit.domain.repository.VisitCommandRepository;

/** Real row-lock ordering, not a race decided by arbitrary sleeps. */
class CaregiverCheckOutConcurrencyIT extends MissedCheckInITSupport {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry,CaregiverCheckOutConcurrencyIT.class,"+05:00","connectionTimeZone=Asia/Singapore");
    }
    @MockitoSpyBean VisitCommandRepository commands;
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    @AfterEach void cleanup() { workers.shutdownNow();reset(commands); }
    private record Gate(CountDownLatch held,CountDownLatch entered,CountDownLatch release) {
        Gate() { this(new CountDownLatch(1),new CountDownLatch(1),new CountDownLatch(1)); }
    }
    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(20,TimeUnit.SECONDS)).isTrue();
    }
    private Gate hold(long id) {
        var gate=new Gate();var first=new AtomicBoolean(true);
        doAnswer(call->{
            if(first.compareAndSet(true,false)) {
                Object locked=call.callRealMethod();gate.held().countDown();await(gate.release());return locked;
            }
            gate.entered().countDown();return call.callRealMethod();
        }).when(commands).lock(id);
        return gate;
    }
    private Map<String,Object> departure(int version) { return Map.of("expectedVersion",version,"clientRequestId",UUID.randomUUID().toString()); }
    private String path(long id) { return "/api/visits/"+id+"/check-out"; }
    @Test void sameKeyConcurrentCheckoutsHaveOneTimeOneCompletionAndOneReceipt() throws Exception {
        long id=plannedVisit();
        try(var a=browser(caregiverName);var b=browser(caregiverName)) {
            body(a.post("/api/visits/"+id+"/check-in",check(0)),200);
            var request=departure(1);var gate=hold(id);
            var first=workers.submit(()->a.post(path(id),request));await(gate.held());
            var second=workers.submit(()->b.post(path(id),request));await(gate.entered());gate.release().countDown();
            var saved=body(first.get(20,TimeUnit.SECONDS),200);var replay=body(second.get(20,TimeUnit.SECONDS),200);
            assertThat(replay.path("checkedOutAt")).isEqualTo(saved.path("checkedOutAt"));
            assertThat(replay.path("replayed").asBoolean()).isTrue();assertThat(version(id)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM caregiver_command_receipt WHERE visit_id=? AND action_code='CHECK_OUT'",Long.class,id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_state_transition WHERE visit_id=? AND to_state='COMPLETED' AND result='APPLIED'",Long.class,id)).isEqualTo(1);
        }
    }
    @ParameterizedTest @CsvSource({"true,TASK","false,TASK","true,HEALTH","false,HEALTH","true,INCIDENT","false,INCIDENT"})
    void checkoutAndOtherWritesRespectBothCommitOrders(boolean checkoutFirst,String other) throws Exception {
        long id=plannedVisit();clock.at(START.plusSeconds(300));
        try(var a=browser(caregiverName);var b=browser(caregiverName)) {
            body(a.post("/api/visits/"+id+"/check-in",check(0)),200);
            long taskId=a.read("/api/visits/"+id+"/work-pack").path("tasks").get(0).path("id").asLong();
            String otherPath=switch(other) {
                case "TASK" -> "/api/visits/"+id+"/tasks/"+taskId+"/complete";
                case "HEALTH" -> "/api/visits/"+id+"/health-records";
                default -> "/api/incidents";
            };
            Map<String,Object> otherInput=switch(other) {
                case "TASK" -> Map.of("expectedVersion",1,"clientRequestId",UUID.randomUUID().toString(),"status","DONE");
                case "HEALTH" -> Map.of("expectedVersion",1,"clientRequestId",UUID.randomUUID().toString(),"healthFlag","ATTENTION","healthNote","Synthetic observation");
                default -> Map.of("visitId",id,"expectedVersion",1,"clientRequestId",UUID.randomUUID().toString(),"category","SERVICE","severity","LOW","description","Synthetic incident");
            };
            var request=departure(1);var gate=hold(id);
            var first=workers.submit(()->a.post(checkoutFirst?path(id):otherPath,checkoutFirst?request:otherInput));await(gate.held());
            var second=workers.submit(()->b.post(checkoutFirst?otherPath:path(id),checkoutFirst?otherInput:request));await(gate.entered());gate.release().countDown();
            body(first.get(20,TimeUnit.SECONDS),checkoutFirst || "TASK".equals(other)?200:201);
            body(second.get(20,TimeUnit.SECONDS),409);assertThat(version(id)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo(checkoutFirst?"COMPLETED":"INCIDENT".equals(other)?"EXCEPTION":"IN_PROGRESS");
            if(checkoutFirst) {
                var refreshed=new HashMap<>(otherInput);refreshed.put("expectedVersion",2);
                body(b.post(otherPath,refreshed),"INCIDENT".equals(other)?201:409);
                assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("COMPLETED");
            } else {
                body(b.post(path(id),departure(2)),"INCIDENT".equals(other)?409:200);
            }
        }
    }
}
