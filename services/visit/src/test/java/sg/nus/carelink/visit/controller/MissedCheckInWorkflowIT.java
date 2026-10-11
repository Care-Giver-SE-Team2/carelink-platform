package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.testsupport.CoreStandIn;
import sg.nus.carelink.testsupport.MissedCheckInITSupport;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.visit.application.VisitReassignment;

/**
 * SYS03 in visit: the scan, its once-per-visit ledger and the visit's version. The incident a missed
 * check-in raises is core's; here core is its stand-in, and what core then does with the incident -
 * routing it to the manager and the families, their views of it, its escalation - core's own tests
 * cover.
 */
class MissedCheckInWorkflowIT extends MissedCheckInITSupport {
    @Autowired VisitReassignment changes;
    @Autowired sg.nus.carelink.visit.domain.repository.MissedCheckInRepository facts;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, MissedCheckInWorkflowIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
        registry.add("carelink.missed-check-in.batch-size", () -> 1);
    }
    @Test void productionThresholdRaisesOneSystemIncidentInCore() throws Exception {
        long id=plannedVisit();
        clock.at(START.plusSeconds(899)); assertThat(scan.scan().triggered()).isZero();
        clock.at(START.plusSeconds(900)); assertThat(scan.scan().triggered()).isZero();
        overdue(); assertThat(scan.scan().triggered()).isEqualTo(1); long event=incident(id);
        assertThat(version(id)).isEqualTo(1); assertThat(count("visit_state_transition", id)).isZero();
        assertSystemTransition(id);
        assertThat(core.incidentsOf(id)).singleElement().satisfies(raised -> {
            assertThat(raised.id()).isEqualTo(event);
            assertThat(raised.elderId()).isEqualTo(elder);
            assertThat(raised.source()).isEqualTo("SYSTEM_MISSED_CHECKIN");
            assertThat(raised.reportedByUserId()).isNull();
        });
        assertThat(scan.trigger(id)).isFalse(); assertThat(count("incident",id)).isEqualTo(1);
    }
    @Test void alertRejectsStaleVersionButAllowsRefreshedCheckInWithoutResolvingIncident() throws Exception {
        long id=plannedVisit();overdue();assertThat(scan.trigger(id)).isTrue();long event=incident(id);
        try(var cg=browser(caregiverName)) {
            var pack=cg.read("/api/visits/"+id+"/work-pack");
            assertThat(pack.path("visit").path("status").asString()).isEqualTo("SCHEDULED");
            assertThat(pack.path("execution").path("allowedActions").valueStream().map(JsonNode::asString).toList()).contains("CHECK_IN").doesNotContain("TASK_RESULT");
            assertThat(pack.path("execution").path("blockedReason").isNull()).isTrue();
            assertThat(pack.path("execution").path("checkedInAt").isNull()).isTrue();
            assertThat(cg.post("/api/visits/"+id+"/check-in",check(0)).statusCode()).isEqualTo(409);
            body(cg.post("/api/visits/"+id+"/check-in",check(1)),200);
            // Checking in leaves the incident to staff, who resolve it in core
            assertThat(core.incidentsOf(id)).singleElement().extracting(CoreStandIn.Raised::status).isEqualTo("OPEN");
            core.resolve(event);
            body(cg.post("/api/visits/"+id+"/check-in",check(1)),409);
        }
        assertThat(scan.trigger(id)).isFalse(); assertThat(count("incident",id)).isEqualTo(1);
        assertThat(count("visit_check_in_record",id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_task WHERE visit_id=?",Long.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("IN_PROGRESS");
        assertThat(version(id)).isEqualTo(2);
        assertSystemTransition(id);
    }
    @Test void timelyCheckInAndStoppedPlanNeverTrigger() throws Exception {
        long id=plannedVisit(); clock.at(START.plusSeconds(300));
        try(var cg=browser(caregiverName)) { body(cg.post("/api/visits/"+id+"/check-in",check(0)),200); }
        overdue(); assertThat(scan.trigger(id)).isFalse();
        clock.at(START); long stopped=plannedVisit();
        long plan=jdbc.queryForObject("SELECT care_plan_id FROM visit WHERE id=?",Long.class,stopped);
        stopPlan(plan, LocalDate.of(2026, 10, 8));
        assertThat(eventsOf(stopped)).containsExactly("VisitScheduled v0","VisitCancelled v1");
        assertThat(lastEvent(stopped,"VisitCancelled").path("reason").asString()).isEqualTo("PLAN_CHANGED");
        overdue(); assertThat(scan.trigger(stopped)).isFalse(); assertThat(count("incident",stopped)).isZero();
    }
    /** The visit's own update fails before visit asks core for anything: nothing anywhere, and the next scan tries again. */
    @Test void failedVisitUpdateLeavesNoFactAndLaterRetrySucceeds() throws Exception {
        long id=plannedVisit(); overdue();
        jdbc.execute("CREATE TRIGGER sys03_parent_fault BEFORE UPDATE ON visit FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic parent failure'");
        try {
            assertThatThrownBy(()->scan.trigger(id)).isInstanceOf(RuntimeException.class);
            assertThat(count("incident",id)).isZero();assertThat(count("visit_missed_check_in_trigger",id)).isZero();
            assertRolledBack(id);
        } finally { jdbc.execute("DROP TRIGGER sys03_parent_fault"); }
        assertThat(scan.trigger(id)).isTrue();assertThat(count("incident",id)).isEqualTo(1);assertSystemTransition(id);
    }
    /**
     * The ledger entry fails after core has raised the incident. visit's facts roll back and core keeps
     * the incident, since the two services share no transaction; the next scan asks again with the same
     * request key (the visit and its due time) and gets that incident back (docs/service-boundaries.md).
     */
    @Test void failedLedgerEntryRollsBackVisitAndTheRetryGetsTheSameIncident() throws Exception {
        long id=plannedVisit(); overdue();
        jdbc.execute("CREATE TRIGGER sys03_source_fault BEFORE INSERT ON visit_missed_check_in_trigger FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic source failure'");
        try {
            assertThatThrownBy(()->scan.trigger(id)).isInstanceOf(RuntimeException.class);
            assertThat(count("visit_missed_check_in_trigger",id)).isZero();
            assertRolledBack(id);
            assertThat(count("incident",id)).isEqualTo(1);
        } finally { jdbc.execute("DROP TRIGGER sys03_source_fault"); }
        assertThat(scan.trigger(id)).isTrue();
        assertThat(count("incident",id)).isEqualTo(1);
        assertThat(incident(id)).isEqualTo(core.incidentsOf(id).getFirst().id());
        assertSystemTransition(id);
    }
    private void assertRolledBack(long id) {
        assertThat(version(id)).isZero();assertThat(count("visit_state_transition",id)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("SCHEDULED");
    }
    private void assertSystemTransition(long id) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_state_transition WHERE visit_id=? AND from_state='SCHEDULED' AND to_state='EXCEPTION' AND result='APPLIED' AND actor_user_id IS NULL",Long.class,id)).isZero();
    }
    @Test void unrelatedIncidentDoesNotSuppressAndResolutionDoesNotReopenVisit() throws Exception {
        long id=plannedVisit();
        // An older, resolved missed check-in on the same visit: the ledger, not "any Incident", is the key.
        var at=LocalDateTime.ofInstant(START,SGT);
        long old=core.raiseMissedCheckIn(new CoreApi.MissedCheckInRequest(elder,id,at.minusDays(7),at.minusDays(7),null)).incidentId();
        core.resolve(old);
        overdue(); assertThat(scan.trigger(id)).isTrue(); assertThat(count("incident",id)).isEqualTo(2);
        long event=incident(id);
        assertThat(event).isNotEqualTo(old);
        core.resolve(event);
        changes.reassign(id,otherCaregiver,new VisitReassignment.Change(null,manager,null,"Fictional replacement"));
        var handedOver=lastEvent(id,"VisitCaregiverChanged");
        assertThat(handedOver.path("caregiverId").asLong()).isEqualTo(otherCaregiver);
        assertThat(handedOver.path("previousCaregiverId").asLong()).isEqualTo(caregiver);
        assertThat(handedOver.path("version").asInt()).isEqualTo(version(id));
        assertThat(jdbc.queryForObject("SELECT caregiver_id FROM visit WHERE id=?",Long.class,id)).isEqualTo(otherCaregiver);
        assertThat(scan.trigger(id)).isFalse(); assertThat(count("incident",id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT triggered_caregiver_id FROM visit_missed_check_in_trigger WHERE visit_id=?",Long.class,id)).isEqualTo(caregiver);
    }
    @Test void failedFirstCandidateDoesNotStarveLaterPage() throws Exception {
        var ids=plannedVisits(3); overdue(); long bad=ids.stream().min(Long::compareTo).orElseThrow();
        jdbc.execute("CREATE TRIGGER sys03_page_fault BEFORE INSERT ON visit_missed_check_in_trigger FOR EACH ROW BEGIN IF NEW.visit_id="+bad+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic first page failure'; END IF; END");
        try {
            var outcome=scan.scan(); assertThat(outcome.failed()).isEqualTo(1); assertThat(outcome.triggered()).isEqualTo(2);
            assertThat(count("visit_missed_check_in_trigger",bad)).isZero();
        } finally { jdbc.execute("DROP TRIGGER sys03_page_fault"); }
        assertThat(scan.scan().triggered()).isEqualTo(1); assertThat(scan.scan().considered()).isZero();
    }
    @Test void candidateCursorAndLedgerUseExistingVisitTimeMapping() throws Exception {
        var ids=plannedVisits(2).stream().sorted().toList();overdue();
        LocalDateTime persistedStart=jdbc.queryForObject("SELECT scheduled_start FROM visit WHERE id=?",
                (row,_) -> row.getTimestamp(1).toLocalDateTime(),ids.getFirst());
        assertThat(persistedStart)
                .as("Manager-published start using main's Visit time mapping").isEqualTo(LocalDateTime.of(2026,10,8,10,5));
        // Separate preservation fixture: SYS03 must not reinterpret an existing deadline.
        var deadline=LocalDateTime.of(2026,10,8,11,5);
        jdbc.update("UPDATE visit SET state_deadline=? WHERE id=?",java.sql.Timestamp.valueOf(deadline),ids.getFirst());
        // Run the complete test JVM in UTC and Singapore separately. Changing its
        // default zone after connections/entities exist is not an application lifecycle.
        {
            var now=LocalDateTime.ofInstant(clock.instant(),SGT);
            var first=facts.candidates(now.minusHours(24),now.minusMinutes(10),null,1).getFirst();
            assertThat(first.scheduledStart()).isEqualTo(LocalDateTime.of(2026,10,8,10,5));
            assertThat(first.visitId()).isEqualTo(ids.getFirst());
            var second=facts.candidates(now.minusHours(24),now.minusMinutes(10),first,1).getFirst();
            assertThat(second.visitId()).isEqualTo(ids.getLast());
            assertThat(facts.candidates(now.minusHours(24),now.minusMinutes(10),second,1)).isEmpty();
            assertThat(scan.scan().triggered()).isEqualTo(2);
            LocalDateTime retainedDeadline=jdbc.queryForObject("SELECT state_deadline FROM visit WHERE id=?",
                    (row,_) -> row.getTimestamp(1).toLocalDateTime(),ids.getFirst());
            assertThat(retainedDeadline).isEqualTo(deadline);
            var recorded=jdbc.queryForObject("SELECT scheduled_start,check_in_due_at,triggered_at FROM visit_missed_check_in_trigger WHERE visit_id=?",
                    (row,_) -> List.of(row.getTimestamp(1).toLocalDateTime(),row.getTimestamp(2).toLocalDateTime(),row.getTimestamp(3).toLocalDateTime()),ids.getFirst());
            assertThat(recorded).containsExactly(LocalDateTime.of(2026,10,8,10,5),
                    LocalDateTime.of(2026,10,8,10,15),LocalDateTime.of(2026,10,8,10,15,1));
        }
    }
    @Test void databaseCandidatesSkipOldFutureUnassignedArrivedCancelledAndTerminalFixtures() {
        overdue();var now=LocalDateTime.ofInstant(clock.instant(),SGT);
        for(String state:List.of("ARRIVED","IN_PROGRESS","COMPLETED","VERIFIED","AUTO_CLOSED","EXCEPTION","CANCELLED")) {
            long id=insert("INSERT INTO visit(elder_id,caregiver_id,scheduled_start,status) VALUES (?,?,?,?)",elder,caregiver,java.sql.Timestamp.valueOf(now.minusMinutes(20)),state);
            assertThat(scan.trigger(id)).isFalse();
        }
        // Negative/boundary fixtures must use the same Timestamp binding as Hibernate
        // and the existing caregiver test support, not raw LocalDateTime setObject.
        insert("INSERT INTO visit(elder_id,caregiver_id,scheduled_start,status) VALUES (?,?,?,'SCHEDULED')",elder,caregiver,java.sql.Timestamp.valueOf(now.minusHours(24).minusSeconds(1)));
        insert("INSERT INTO visit(elder_id,caregiver_id,scheduled_start,status) VALUES (?,?,?,'SCHEDULED')",elder,caregiver,java.sql.Timestamp.valueOf(now.plusHours(1)));
        insert("INSERT INTO visit(elder_id,scheduled_start,status) VALUES (?,?,'SCHEDULED')",elder,java.sql.Timestamp.valueOf(now.minusMinutes(20)));
        insert("INSERT INTO visit(elder_id,caregiver_id,scheduled_start,status,checked_in_at) VALUES (?,?,?,'SCHEDULED',?)",elder,caregiver,java.sql.Timestamp.valueOf(now.minusMinutes(20)),java.sql.Timestamp.valueOf(now));
        assertThat(scan.scan().triggered()).isZero();
        long boundary=insert("INSERT INTO visit(elder_id,caregiver_id,scheduled_start,status) VALUES (?,?,?,'SCHEDULED')",elder,caregiver,java.sql.Timestamp.valueOf(now.minusHours(24)));
        assertThat(scan.scan().triggered()).isEqualTo(1);assertThat(incident(boundary)).isPositive();
    }
}
