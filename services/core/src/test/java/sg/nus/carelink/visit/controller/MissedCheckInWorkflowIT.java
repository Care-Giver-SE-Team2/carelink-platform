package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import sg.nus.carelink.incident.application.EscalationScanService;
import sg.nus.carelink.testsupport.MissedCheckInITSupport;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.visit.application.VisitReassignment;

class MissedCheckInWorkflowIT extends MissedCheckInITSupport {
    @Autowired EscalationScanService escalation;
    @Autowired sg.nus.carelink.incident.application.IncidentService incidents;
    @Autowired VisitReassignment changes;
    @Autowired sg.nus.carelink.visit.domain.repository.MissedCheckInRepository facts;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, MissedCheckInWorkflowIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
        registry.add("carelink.missed-check-in.batch-size", () -> 1);
    }
    @Test void productionThresholdRoutesOnceToManagerAndIndependentFamilies() throws Exception {
        long id=plannedVisit();
        clock.at(START.plusSeconds(899)); assertThat(scan.scan().triggered()).isZero();
        clock.at(START.plusSeconds(900)); assertThat(scan.scan().triggered()).isZero();
        overdue(); assertThat(scan.scan().triggered()).isEqualTo(1); long event=incident(id);
        assertThat(version(id)).isEqualTo(1); assertThat(count("visit_state_transition", id)).isEqualTo(1);
        assertSystemTransition(id);
        try(var mgr=browser(managerName);var cg=browser(caregiverName);var a=browser(familyName);var b=browser(secondFamilyName)) {
            var detail=mgr.read("/api/incidents/"+event).path("incident");
            assertThat(detail.toString()).contains("SYSTEM_MISSED_CHECKIN","Assigned caregiver has not checked in","SERVICE","MEDIUM");
            assertThat(detail.path("reportedByUserId").isNull()).isTrue();
            assertThat(detail.path("responderUserId").asLong()).isEqualTo(manager);
            assertThat(detail.path("respondBy").asString()).isEqualTo("2026-10-08T10:30:01");
            assertThat(cg.get("/api/incidents/"+event).statusCode()).isEqualTo(403);
            assertThat(a.read("/api/notifications/me").path("items").get(0).path("resourceId").asLong()).isEqualTo(event);
            assertThat(b.read("/api/notifications/me").path("items").get(0).path("resourceId").asLong()).isEqualTo(event);
            String path="/api/family/incidents/"+event;
            assertThat(a.read(path).toString()).doesNotContain("responderUserId","latitude");
            body(a.post("/api/incidents/"+event+"/view",null),200);
            body(a.post("/api/incidents/"+event+"/acknowledge",Map.of("responseNote","Family informed")),200);
            assertThat(b.read(path).path("acknowledgement").path("acknowledgedAt").isNull()).isTrue();
        }
        assertThat(scan.trigger(id)).isFalse(); assertThat(count("incident",id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE incident_id=?",Long.class,event)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE recipient_user_id=? AND resource_id=? AND event_type='INCIDENT_RAISED'",Long.class,manager,event)).isEqualTo(1);
    }
    @Test void alertBlocksStaleAndRefreshedCheckInEvenAfterIncidentResolution() throws Exception {
        long id=plannedVisit();overdue();assertThat(scan.trigger(id)).isTrue();long event=incident(id);
        try(var mgr=browser(managerName);var cg=browser(caregiverName)) {
            var pack=cg.read("/api/visits/"+id+"/work-pack");
            assertThat(pack.path("visit").path("status").asString()).isEqualTo("EXCEPTION");
            assertThat(pack.path("execution").path("allowedActions").valueStream().map(JsonNode::asString).toList()).doesNotContain("CHECK_IN","TASK_RESULT");
            assertThat(pack.path("execution").path("blockedReason").asString()).isEqualTo("VISIT_EXECUTION_NOT_ALLOWED");
            assertThat(pack.path("execution").path("checkedInAt").isNull()).isTrue();
            assertThat(cg.post("/api/visits/"+id+"/check-in",check(0)).statusCode()).isEqualTo(409);
            assertThat(body(cg.post("/api/visits/"+id+"/check-in",check(1)),409).toString()).contains("VISIT_EXECUTION_NOT_ALLOWED");
            assertThat(mgr.read("/api/incidents/"+event).path("incident").path("status").asString()).isEqualTo("OPEN");
            body(mgr.post("/api/incidents/"+event+"/claim",Map.of()),200);
            body(mgr.post("/api/incidents/"+event+"/resolve",Map.of("resolutionNote","Checked attendance and completed review")),200);
            assertThat(mgr.read("/api/incidents/"+event).path("incident").path("status").asString()).isEqualTo("RESOLVED");
            body(cg.post("/api/visits/"+id+"/check-in",check(1)),409);
        }
        assertThat(scan.trigger(id)).isFalse(); assertThat(count("incident",id)).isEqualTo(1);
        assertThat(count("visit_check_in_record",id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_task WHERE visit_id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("EXCEPTION");
        assertThat(version(id)).isEqualTo(1);
        assertSystemTransition(id);
    }
    @Test void timelyCheckInAndStoppedPlanNeverTrigger() throws Exception {
        long id=plannedVisit(); clock.at(START.plusSeconds(300));
        try(var cg=browser(caregiverName)) { body(cg.post("/api/visits/"+id+"/check-in",check(0)),200); }
        overdue(); assertThat(scan.trigger(id)).isFalse();
        clock.at(START); long stopped=plannedVisit();
        long plan=jdbc.queryForObject("SELECT care_plan_id FROM visit WHERE id=?",Long.class,stopped);
        try(var mgr=browser(managerName)) { body(mgr.post("/api/care-plans/"+plan+"/stop",Map.of("effectiveDate","2026-10-08","reason","Fictional cancellation")),200); }
        overdue(); assertThat(scan.trigger(stopped)).isFalse(); assertThat(count("incident",stopped)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"incident","incident_log","notification","visit_state_transition","visit_missed_check_in_trigger"})
    void sourcePersistenceFailureRollsBackAllFactsAndLaterRetrySucceeds(String table) throws Exception {
        long id=plannedVisit(); overdue();
        jdbc.execute("CREATE TRIGGER sys03_source_fault BEFORE INSERT ON "+table+" FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic source failure'");
        try {
            assertThatThrownBy(()->scan.trigger(id)).isInstanceOf(RuntimeException.class);
            assertThat(count("incident",id)).isZero(); assertThat(count("visit_missed_check_in_trigger",id)).isZero();
            assertRolledBack(id);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE elder_id=?",Long.class,elder)).isZero();
        } finally { jdbc.execute("DROP TRIGGER sys03_source_fault"); }
        assertThat(scan.trigger(id)).isTrue(); assertThat(count("incident",id)).isEqualTo(1);
        assertSystemTransition(id);
    }
    @Test void parentUpdateFailureCannotLeaveIncidentTimelineOrNotification() throws Exception {
        long id=plannedVisit(); overdue();
        jdbc.execute("CREATE TRIGGER sys03_parent_fault BEFORE UPDATE ON visit FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic parent failure'");
        try {
            assertThatThrownBy(()->scan.trigger(id)).isInstanceOf(RuntimeException.class);
            assertThat(count("incident",id)).isZero();assertThat(count("visit_missed_check_in_trigger",id)).isZero();
            assertRolledBack(id);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE elder_id=?",Long.class,elder)).isZero();
        } finally { jdbc.execute("DROP TRIGGER sys03_parent_fault"); }
        assertThat(scan.trigger(id)).isTrue();assertSystemTransition(id);
    }
    @Test void incidentEntryRequiresTheCallerTransaction() throws Exception {
        long id=plannedVisit(); overdue();var at=LocalDateTime.ofInstant(clock.instant(),SGT);
        assertThatThrownBy(()->incidents.raiseForMissedCheckIn(elder,id,at.minusSeconds(1),at))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(count("incident",id)).isZero();assertRolledBack(id);
    }
    private void assertRolledBack(long id) {
        assertThat(version(id)).isZero();assertThat(count("visit_state_transition",id)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident_log l JOIN incident i ON i.id=l.incident_id WHERE i.visit_id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE resource_type='INCIDENT' AND recipient_user_id IN (?,?,?,?)",Long.class,manager,caregiverUser,family,secondFamilyUser)).isZero();
    }
    private void assertSystemTransition(long id) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_state_transition WHERE visit_id=? AND from_state='SCHEDULED' AND to_state='EXCEPTION' AND result='APPLIED' AND actor_user_id IS NULL",Long.class,id)).isEqualTo(1);
    }
    @Test void failedFamilyConsumerDoesNotRollbackSourceOrPreventOtherFamily() throws Exception {
        long id=plannedVisit(); overdue();
        jdbc.execute("CREATE TRIGGER sys03_consumer_fault BEFORE INSERT ON notification FOR EACH ROW BEGIN IF NEW.recipient_user_id="+family+" AND NEW.resource_type='INCIDENT' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic consumer failure'; END IF; END");
        try {
            assertThat(scan.trigger(id)).isTrue(); long event=incident(id);
            assertThat(jdbc.queryForObject("SELECT state FROM family_alert_event WHERE incident_id=?",String.class,event)).isEqualTo("FAILED");
            long profile=jdbc.queryForObject("SELECT id FROM family_member WHERE user_id=?",Long.class,family);
            assertThat(jdbc.queryForObject("SELECT status FROM family_alert_delivery WHERE family_member_id=? AND event_id=(SELECT event_id FROM family_alert_event WHERE incident_id=?)",String.class,profile,event)).isEqualTo("FAILED");
            try(var b=browser(secondFamilyName)) { assertThat(b.read("/api/notifications/me").path("items").get(0).path("resourceId").asLong()).isEqualTo(event); }
            assertThat(scan.trigger(id)).isFalse(); assertThat(count("incident",id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?",String.class,id)).isEqualTo("EXCEPTION");
            assertThat(version(id)).isEqualTo(1);assertSystemTransition(id);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident_log WHERE incident_id=? AND action='REPORTED'",Long.class,event)).isEqualTo(1);
        } finally { jdbc.execute("DROP TRIGGER sys03_consumer_fault"); }
    }
    @Test void actualOverdueEscalationReusesIncidentAndFamilyWindows() throws Exception {
        long id=plannedVisit(); overdue(); scan.trigger(id); long event=incident(id);
        try(var a=browser(familyName)) {
            var before=a.read("/api/family/incidents/"+event).path("acknowledgeBy");
            body(a.post("/api/incidents/"+event+"/acknowledge",Map.of("responseNote","Already aware")),200);
            clock.at(START.plusSeconds(1801)); assertThat(escalation.sweep()).isGreaterThanOrEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM incident WHERE id=?",String.class,event)).isEqualTo("UNRESOLVED_ESCALATED");
            assertThat(a.read("/api/notifications/me").path("items").valueStream().map(row->row.path("eventType").asString()).toList()).contains("INCIDENT_RAISED","INCIDENT_UNRESOLVED");
            assertThat(a.read("/api/family/incidents/"+event).path("acknowledgeBy")).isEqualTo(before);
            assertThat(a.read("/api/family/incidents/"+event).path("acknowledgement").path("acknowledgedAt").isNull()).isFalse();
            assertThat(escalation.sweep()).isZero(); assertThat(scan.trigger(id)).isFalse();
        }
    }
    @Test void emptyManagerChainStillRaisesOneUnresolvedSystemIncident() throws Exception {
        long id=plannedVisit(); jdbc.update("DELETE FROM user_role WHERE role='MANAGER'"); overdue();
        assertThat(scan.trigger(id)).isTrue(); long event=incident(id);
        assertThat(jdbc.queryForObject("SELECT status FROM incident WHERE id=?",String.class,event)).isEqualTo("UNRESOLVED_ESCALATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE incident_id=?",Long.class,event)).isEqualTo(2);
        assertThat(scan.trigger(id)).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"REVOKED","EXPIRED","UNBOUND"})
    void unauthorizedFamilyCannotReceiveOrReadSystemAlert(String condition) throws Exception {
        long id=plannedVisit();
        if(condition.equals("UNBOUND")) jdbc.update("DELETE FROM elder_family_binding WHERE elder_id=? AND family_member_id=?",elder,secondFamily);
        else if(condition.equals("REVOKED")) jdbc.update("UPDATE elder_family_binding SET status='REVOKED' WHERE elder_id=? AND family_member_id=?",elder,secondFamily);
        else jdbc.update("UPDATE elder_family_binding SET expires_at=? WHERE elder_id=? AND family_member_id=?",LocalDateTime.ofInstant(START,SGT),elder,secondFamily);
        overdue(); scan.trigger(id); long event=incident(id);
        try(var b=browser(secondFamilyName)) {
            assertThat(b.read("/api/notifications/me").path("totalElements").asInt()).isZero();
            assertThat(b.get("/api/family/incidents/"+event).statusCode()).isEqualTo(403);
        }
    }
    @Test void unrelatedIncidentDoesNotSuppressAndResolutionDoesNotReopenVisit() throws Exception {
        long id=plannedVisit();
        // Independent negative fixture proves the ledger, not "any Incident", is the key.
        jdbc.update("INSERT INTO incident(elder_id,visit_id,source,category,severity,status,description) VALUES (?,?,'SYSTEM_MISSED_CHECKIN','SERVICE','MEDIUM','RESOLVED','Independent old event')",elder,id);
        overdue(); assertThat(scan.trigger(id)).isTrue(); assertThat(count("incident",id)).isEqualTo(2);
        long event=incident(id);
        try(var mgr=browser(managerName)) {
            body(mgr.post("/api/incidents/"+event+"/claim",Map.of()),200);
            body(mgr.post("/api/incidents/"+event+"/resolve",Map.of("resolutionNote","Reviewed")),200);
        }
        assertThatThrownBy(()->changes.reassign(id,otherCaregiver,new VisitReassignment.Change(null,manager,null,"Fictional replacement")))
                .isInstanceOf(sg.nus.carelink.shared.error.BusinessRuleViolation.class).hasMessageContaining("EXCEPTION");
        assertThat(scan.trigger(id)).isFalse(); assertThat(count("incident",id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT triggered_caregiver_id FROM visit_missed_check_in_trigger WHERE visit_id=?",Long.class,id)).isEqualTo(caregiver);
    }
    @Test void failedFirstCandidateDoesNotStarveLaterPage() throws Exception {
        var ids=plannedVisits(3); overdue(); long bad=ids.stream().min(Long::compareTo).orElseThrow();
        jdbc.execute("CREATE TRIGGER sys03_page_fault BEFORE INSERT ON visit_missed_check_in_trigger FOR EACH ROW BEGIN IF NEW.visit_id="+bad+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic first page failure'; END IF; END");
        try {
            var outcome=scan.scan(); assertThat(outcome.failed()).isEqualTo(1); assertThat(outcome.triggered()).isEqualTo(2);
            assertThat(count("incident",bad)).isZero();
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
