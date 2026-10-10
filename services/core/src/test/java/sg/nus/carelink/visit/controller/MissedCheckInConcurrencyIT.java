package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import sg.nus.carelink.testsupport.MissedCheckInITSupport;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.visit.application.VisitReassignment;
import sg.nus.carelink.visit.domain.repository.VisitCommandRepository;
import sg.nus.carelink.visit.domain.repository.VisitRepository;

/** Latches hold real MySQL locks/snapshots; no random sleep decides the winning operation. */
class MissedCheckInConcurrencyIT extends MissedCheckInITSupport {
    @MockitoSpyBean VisitCommandRepository commands;
    @MockitoSpyBean VisitRepository repository;
    @Autowired VisitReassignment changes;
    @Autowired sg.nus.carelink.visit.application.VisitScheduling scheduling;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    @Autowired sg.nus.carelink.visit.domain.repository.MissedCheckInRepository facts;
    @Autowired sg.nus.carelink.incident.application.MissedCheckInIncidentGateway incidentGateway;
    private final ExecutorService workers=Executors.newFixedThreadPool(3);
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, MissedCheckInConcurrencyIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
    }
    @AfterEach void closeWorkers() { workers.shutdownNow(); reset(commands,repository); }
    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(20,TimeUnit.SECONDS)).as("controlled synchronization point").isTrue();
    }
    private record Gate(CountDownLatch held, CountDownLatch entered, CountDownLatch release) {
        Gate() { this(new CountDownLatch(1),new CountDownLatch(1),new CountDownLatch(1)); }
    }
    private Gate holdFirstCommand(long id) {
        var gate=new Gate();var first=new AtomicBoolean(true);
        doAnswer(call->{
            if(first.compareAndSet(true,false)) {
                Object current=call.callRealMethod(); gate.held().countDown(); await(gate.release()); return current;
            }
            gate.entered().countDown(); return call.callRealMethod();
        }).when(commands).lock(id);
        return gate;
    }
    @Test void twoScannersSerializeAndOnlyOneDurableSourceExists() throws Exception {
        long id=plannedVisit();overdue();var gate=holdFirstCommand(id);
        var a=workers.submit(()->scan.trigger(id));await(gate.held());
        var b=workers.submit(()->scan.trigger(id));await(gate.entered());
        gate.release().countDown();assertThat(a.get(20,TimeUnit.SECONDS)).isTrue();assertThat(b.get(20,TimeUnit.SECONDS)).isFalse();
        assertOneFact(id);assertThat(version(id)).isEqualTo(1);
    }
    @Test void committedCheckInWinsAndScannerRefreshesItsCandidate() throws Exception {
        long id=plannedVisit();overdue();var gate=holdFirstCommand(id);
        try(var cg=browser(caregiverName)) {
            var check=workers.submit(()->cg.post("/api/visits/"+id+"/check-in",check(0)));await(gate.held());
            var alarm=workers.submit(()->scan.trigger(id));await(gate.entered());gate.release().countDown();
            body(check.get(20,TimeUnit.SECONDS),200);assertThat(alarm.get(20,TimeUnit.SECONDS)).isFalse();
            assertThat(count("incident",id)).isZero();assertThat(count("visit_missed_check_in_trigger",id)).isZero();
            assertThat(count("visit_check_in_record",id)).isEqualTo(1);
        }
    }
    @Test void scannerWinsStaleVersionRejectedButRefreshedCheckInSucceeds() throws Exception {
        long id=plannedVisit();overdue();var gate=holdFirstCommand(id);
        var alarm=workers.submit(()->scan.trigger(id));await(gate.held());
        try(var cg=browser(caregiverName)) {
            var check=workers.submit(()->cg.post("/api/visits/"+id+"/check-in",check(0)));await(gate.entered());gate.release().countDown();
            assertThat(alarm.get(20,TimeUnit.SECONDS)).isTrue();body(check.get(20,TimeUnit.SECONDS),409);
            body(cg.post("/api/visits/"+id+"/check-in",check(1)),200);assertOneFact(id);
            assertThat(count("visit_check_in_record",id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("IN_PROGRESS");
            assertThat(jdbc.queryForObject("SELECT status FROM incident WHERE id=?",String.class,incident(id))).isEqualTo("OPEN");
        }
    }
    private Object managerChange(String action,long id) {
        var why=new VisitReassignment.Change(null,manager,null,"Controlled fictional change");
        return switch(action) {
            case "reassign" -> { changes.reassign(id,otherCaregiver,why); yield null; }
            case "cancel" -> { changes.callOff(id,why); yield null; }
            case "move" -> changes.moveTo(id,LocalDateTime.ofInstant(START.plusSeconds(1800),SGT),otherCaregiver,why);
            default -> throw new IllegalArgumentException(action);
        };
    }
    @ParameterizedTest @ValueSource(strings={"cancel","reassign","move"})
    void managerCommitFirstMakesScannerUseCurrentStatusAssignmentAndTime(String action) throws Exception {
        long id=plannedVisit();overdue();var gate=new Gate();var first=new AtomicBoolean(true);
        doAnswer(call->{Object result=call.callRealMethod();em.flush();
            if(first.compareAndSet(true,false)) { gate.held().countDown();await(gate.release()); } return result;
        }).when(repository).save(argThat(v->v!=null && Objects.equals(v.id(),id)));
        doAnswer(call->{gate.entered().countDown();return call.callRealMethod();}).when(commands).lock(id);
        var change=workers.submit(()->managerChange(action,id));await(gate.held());
        var alarm=workers.submit(()->scan.trigger(id));await(gate.entered());gate.release().countDown();
        var moved=change.get(20,TimeUnit.SECONDS);boolean raised=alarm.get(20,TimeUnit.SECONDS);
        assertThat(raised).isEqualTo(action.equals("reassign"));
        if(raised) { assertOneFact(id);assertThat(jdbc.queryForObject("SELECT triggered_caregiver_id FROM visit_missed_check_in_trigger WHERE visit_id=?",Long.class,id)).isEqualTo(otherCaregiver); }
        else { assertThat(count("incident",id)).isZero(); }
        if(action.equals("move")) {
            long newId=(Long)moved;assertThat(scan.trigger(newId)).isFalse();
            clock.at(START.plusSeconds(2401));assertThat(scan.trigger(newId)).isTrue();assertOneFact(newId);
        }
    }
    @ParameterizedTest @ValueSource(strings={"cancel","reassign","move"})
    void scannerCommitsWhileManagerHoldsOldSnapshotPreventsStaleOverwrite(String action) throws Exception {
        long id=plannedVisit();overdue();var gate=new Gate();var first=new AtomicBoolean(true);
        doAnswer(call->{Object result=call.callRealMethod();
            if(first.compareAndSet(true,false)) { gate.held().countDown();await(gate.release()); } return result;
        }).when(repository).findById(id);
        var change=workers.submit(()->managerChange(action,id));await(gate.held());
        assertThat(scan.trigger(id)).isTrue();gate.release().countDown();
        assertThatThrownBy(()->change.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
        assertOneFact(id);assertThat(version(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT caregiver_id FROM visit WHERE id=?",Long.class,id)).isEqualTo(caregiver);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_assignment WHERE visit_id=?",Long.class,id)).isZero();
        reset(repository);
        managerChange(action,id);
        assertThat(scan.trigger(id)).isFalse();
    }
    @Test void scannerWinsThenPlanCancellationReReadsLockedVersionInsteadOfStaleOverwrite() throws Exception {
        long id=plannedVisit();overdue();long plan=jdbc.queryForObject("SELECT care_plan_id FROM visit WHERE id=?",Long.class,id);
        var from=LocalDateTime.ofInstant(START,SGT);
        int otherScheduled=jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE care_plan_id=? AND scheduled_start>=? AND status='SCHEDULED' AND id<>?",
                Integer.class,plan,java.sql.Timestamp.valueOf(from),id);
        var gate=holdFirstCommand(id);
        var alarm=workers.submit(()->scan.trigger(id));await(gate.held());
        {
            var stopped=workers.submit(()->scheduling.cancelUntouchedFrom(plan,from));await(gate.entered());
            gate.release().countDown();assertThat(alarm.get(20,TimeUnit.SECONDS)).isTrue();assertThat(stopped.get(20,TimeUnit.SECONDS)).isEqualTo(otherScheduled+1);
            assertOneFact(id);assertThat(version(id)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE care_plan_id=? AND scheduled_start>=? AND status='SCHEDULED'",
                    Integer.class,plan,java.sql.Timestamp.valueOf(from))).isZero();
            assertThat(scan.trigger(id)).isFalse();
        }
    }
    @Test void planStopHoldsWriteLockThenScannerSeesCommittedCancellation() throws Exception {
        long id=plannedVisit();long plan=jdbc.queryForObject("SELECT care_plan_id FROM visit WHERE id=?",Long.class,id);
        var gate=holdFirstCommand(id);
        try(var mgr=browser(managerName)) {
            var stopped=workers.submit(()->mgr.post("/api/care-plans/"+plan+"/stop",Map.of("effectiveDate","2026-10-08","reason","Controlled stop")));await(gate.held());
            overdue();
            var alarm=workers.submit(()->scan.trigger(id));await(gate.entered());gate.release().countDown();
            body(stopped.get(20,TimeUnit.SECONDS),200);assertThat(alarm.get(20,TimeUnit.SECONDS)).isFalse();assertThat(count("incident",id)).isZero();
        }
    }
    @Test void planStoppedAfterVisitWasDueRetainsItForAttendanceReviewAsExistingMg03Requires() throws Exception {
        long id=plannedVisit();overdue();long plan=jdbc.queryForObject("SELECT care_plan_id FROM visit WHERE id=?",Long.class,id);
        try(var mgr=browser(managerName)) {
            body(mgr.post("/api/care-plans/"+plan+"/stop",Map.of("effectiveDate","2026-10-08","reason","Stopped after service was due")),200);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("SCHEDULED");
        assertThat(scan.trigger(id)).isTrue();assertOneFact(id);
    }
    @Test void independentNewScannerInstanceReadsPersistedLedgerInsteadOfAnInMemorySet() throws Exception {
        long id=plannedVisit();overdue();assertThat(scan.trigger(id)).isTrue();
        // Alert retained SCHEDULED; restart must still read the persisted once-per-Visit ledger.
        // New service has no previous scan memory. Database facts alone must suppress it.
        var recreated=new sg.nus.carelink.visit.application.MissedCheckInScanService(transactions,commands,
                facts, incidentGateway,
                new sg.nus.carelink.visit.domain.service.MissedCheckInPolicy(Duration.ofMinutes(10),Duration.ofDays(1)),
                new sg.nus.carelink.visit.application.MissedCheckInScanService.Settings(true,200),clock);
        assertThat(recreated.trigger(id)).isFalse();assertOneFact(id);
    }
    private void assertOneFact(long id) {
        assertThat(count("incident",id)).isEqualTo(1);assertThat(count("visit_missed_check_in_trigger",id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_state_transition WHERE visit_id=? AND from_state='SCHEDULED' AND to_state='EXCEPTION' AND result='APPLIED' AND actor_user_id IS NULL",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE incident_id=? AND event_type='INCIDENT_RAISED'",Long.class,incident(id))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE recipient_user_id=? AND resource_id=? AND event_type='INCIDENT_RAISED'",Long.class,family,incident(id))).isEqualTo(1);
    }
}
