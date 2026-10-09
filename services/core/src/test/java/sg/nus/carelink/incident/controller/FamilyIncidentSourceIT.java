package sg.nus.carelink.incident.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import sg.nus.carelink.incident.application.EscalationService;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.testsupport.CaregiverHttpITSupport;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.visit.domain.repository.CaregiverCommandStore;

/** Real CG04 transactions feed FM05; no test publishes the family event. @author Wang Zhili */
class FamilyIncidentSourceIT extends CaregiverHttpITSupport {
    @Autowired private EscalationService escalation;
    @MockitoSpyBean private CaregiverCommandStore receipts;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, FamilyIncidentSourceIT.class, null);
    }

    @BeforeEach void makeVisitDue() {
        // SharedMySql isolates the class; clear only its previous incident scenarios.
        for (String table : List.of("caregiver_command_receipt", "family_alert_window", "family_alert_delivery",
                "family_alert_event", "notification", "incident_acknowledgement", "incident_log", "incident")) {
            jdbc.update("DELETE FROM " + table);
        }
        LocalDateTime now = LocalDateTime.now(Incident.CARELINK_ZONE);
        jdbc.update("UPDATE visit SET scheduled_start=?, scheduled_end=? WHERE id=?",
                now.minusMinutes(5), now.plusHours(1), visit);
    }

    @Test void committedReportCreatesOneSafeFamilyMessageAndPreservesStaffRouting() throws Exception {
        // The CG04 receipt is saved AFTER incident routing. Neither event nor family message
        // may exist yet: its failure must still be able to roll back the whole source command.
        doAnswer(call -> {
            assertThat(count("family_alert_event")).isZero();
            assertThat(familyMessages()).isZero();
            return call.callRealMethod();
        }).when(receipts).save(any());
        try (var caregiverBrowser = browser(caregiverName); var familyBrowser = browser(familyName)) {
            var request = input();
            long id = body(caregiverBrowser.post("/api/incidents", request), 201).path("report").path("id").asLong();
            assertThat(familyMessages()).isEqualTo(1);
            var events = jdbc.queryForList("SELECT * FROM family_alert_event WHERE elder_id=?", elder);
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.get("event_type")).isEqualTo("INCIDENT_RAISED");
                assertThat(event.get("incident_id")).isEqualTo(id);
                assertThat(event.get("state")).isEqualTo("PROCESSED");
                assertThat(UUID.fromString(event.get("event_id").toString())).isNotNull();
            });
            var messages = jdbc.queryForList("SELECT * FROM notification WHERE recipient_user_id=? AND resource_id=?", family, id);
            assertThat(messages).singleElement().satisfies(message -> {
                assertThat(message.get("status")).isEqualTo("PENDING");
                assertThat(message.get("body").toString()).doesNotContain("Private source narrative");
                assertThat(message.get("read_at")).isNull();
            });
            assertThat(count("family_alert_delivery")).isEqualTo(1);
            assertThat(count("family_alert_window")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT responder_user_id FROM incident WHERE id=?", Long.class, id)).isNotNull();
            for (long staff : List.of(manager, caregiverUser)) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE resource_id=? AND recipient_user_id=? AND event_type='INCIDENT_RAISED'",
                        Long.class, id, staff)).isEqualTo(1);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE resource_id=? AND event_type='INCIDENT_ASSIGNED'", Long.class, id)).isEqualTo(1);
            var detail = familyBrowser.read("/api/family/incidents/" + id);
            assertThat(detail.path("acknowledgeBy").isNull()).isFalse();
            assertThat(count("incident_acknowledgement")).isZero();
            assertThat(body(caregiverBrowser.post("/api/incidents", request), 200).path("replayed").asBoolean()).isTrue();
            assertThat(jdbc.queryForList("SELECT * FROM family_alert_event WHERE elder_id=?", elder)).isEqualTo(events);
            assertThat(jdbc.queryForList("SELECT * FROM notification WHERE recipient_user_id=? AND resource_id=?", family, id)).isEqualTo(messages);
            assertThat(count("family_alert_window")).isEqualTo(1);
        }
    }

    @Test void initiallyEmptyChainPublishesTwoDifferentFactsWithoutLegacyDuplicates() throws Exception {
        removeManagers();
        try (var caregiverBrowser = browser(caregiverName)) {
            long id = body(caregiverBrowser.post("/api/incidents", input()), 201).path("report").path("id").asLong();
            assertThat(jdbc.queryForObject("SELECT status FROM incident WHERE id=?", String.class, id)).isEqualTo("UNRESOLVED_ESCALATED");
            assertThat(eventTypes()).containsExactlyInAnyOrder("INCIDENT_RAISED", "INCIDENT_UNRESOLVED");
            assertThat(familyMessages()).isEqualTo(2);
            assertThat(count("family_alert_delivery")).isEqualTo(2);
            assertThat(count("family_alert_window")).isEqualTo(1);
        }
    }

    @Test void realOverdueEscalationPublishesUnresolvedOnceAndKeepsFirstWindow() throws Exception {
        try (var caregiverBrowser = browser(caregiverName)) {
            long id = body(caregiverBrowser.post("/api/incidents", input()), 201).path("report").path("id").asLong();
            var windows = jdbc.queryForList("SELECT * FROM family_alert_window WHERE incident_id=?", id);
            removeManagers();
            LocalDateTime now = LocalDateTime.now(Incident.CARELINK_ZONE);
            jdbc.update("UPDATE incident SET respond_by=? WHERE id=?", java.sql.Timestamp.valueOf(now.minusMinutes(1)), id);
            assertThat(escalation.escalateIfStillOverdue(id, now)).isTrue();
            assertThat(eventTypes()).containsExactlyInAnyOrder("INCIDENT_RAISED", "INCIDENT_UNRESOLVED");
            assertThat(familyMessages()).isEqualTo(2);
            assertThat(jdbc.queryForList("SELECT * FROM family_alert_window WHERE incident_id=?", id)).isEqualTo(windows);
            assertThat(escalation.escalateIfStillOverdue(id, now)).isFalse();
            assertThat(familyMessages()).isEqualTo(2);
            assertThat(count("family_alert_event")).isEqualTo(2);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void failureAfterRoutingRollsBackBothSourceAndRegisteredEvents(boolean emptyChain) throws Exception {
        if (emptyChain) removeManagers();
        doAnswer(call -> {
            assertThat(count("family_alert_event")).isZero();
            assertThat(familyMessages()).isZero();
            throw new IllegalStateException("Forced CG04 receipt failure after routing");
        }).when(receipts).save(any());
        try (var caregiverBrowser = browser(caregiverName)) {
            assertThat(caregiverBrowser.post("/api/incidents", input()).statusCode()).isEqualTo(503);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?", String.class, visit)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT version FROM visit WHERE id=?", Integer.class, visit)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident WHERE visit_id=?", Long.class, visit)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM caregiver_command_receipt WHERE visit_id=?", Long.class, visit)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_state_transition WHERE visit_id=? AND result='APPLIED'", Long.class, visit)).isZero();
        for (String table : List.of("incident_log", "notification", "family_alert_event", "family_alert_delivery", "family_alert_window")) {
            assertThat(count(table)).as(table).isZero();
        }
    }

    @Test void concurrentSuccessfulCommandReplayDoesNotRerouteOrRepublish() throws Exception {
        try (var first = browser(caregiverName); var second = browser(caregiverName)) {
            var request = input();
            var pending = CompletableFuture.supplyAsync(() -> {
                try { return first.post("/api/incidents", request); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            var response = second.post("/api/incidents", request);
            assertThat(List.of(pending.get().statusCode(), response.statusCode())).containsExactlyInAnyOrder(201, 200);
            assertThat(count("family_alert_event")).isEqualTo(1);
            assertThat(familyMessages()).isEqualTo(1);
            assertThat(count("family_alert_window")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident WHERE visit_id=?", Long.class, visit)).isEqualTo(1);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"notification", "family_alert_event"})
    void observerStorageFailureDoesNotTurnCommittedReportIntoFailedHttpResponse(String table) throws Exception {
        String condition = table.equals("notification") ? "NEW.recipient_user_id=" + family : "NEW.elder_id=" + elder;
        jdbc.execute("CREATE TRIGGER fm05_source_fault BEFORE INSERT ON " + table + " FOR EACH ROW BEGIN IF "
                + condition + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Forced family storage failure'; END IF; END");
        try (var caregiverBrowser = browser(caregiverName)) {
            long id = body(caregiverBrowser.post("/api/incidents", input()), 201).path("report").path("id").asLong();
            assertThat(jdbc.queryForObject("SELECT status FROM visit WHERE id=?", String.class, visit)).isEqualTo("EXCEPTION");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM caregiver_command_receipt WHERE visit_id=?", Long.class, visit)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE resource_id=? AND recipient_user_id=?", Long.class, id, manager)).isGreaterThan(0);
            assertThat(familyMessages()).isZero();
            assertThat(count("family_alert_window")).isZero();
            if (table.equals("notification")) {
                assertThat(jdbc.queryForObject("SELECT state FROM family_alert_event WHERE incident_id=?", String.class, id)).isEqualTo("FAILED");
                assertThat(jdbc.queryForObject("SELECT status FROM family_alert_delivery WHERE event_id=(SELECT event_id FROM family_alert_event WHERE incident_id=?)", String.class, id)).isEqualTo("FAILED");
            }
        } finally { jdbc.execute("DROP TRIGGER fm05_source_fault"); }
    }

    @Test void revokedRecipientIsRecordedInsteadOfReceivingLegacyMessage() throws Exception {
        jdbc.update("UPDATE elder_family_binding SET status='REVOKED' WHERE elder_id=?", elder);
        try (var caregiverBrowser = browser(caregiverName)) {
            long id = body(caregiverBrowser.post("/api/incidents", input()), 201).path("report").path("id").asLong();
            assertThat(jdbc.queryForObject("SELECT state FROM family_alert_event WHERE incident_id=?", String.class, id)).isEqualTo("NO_RECIPIENTS");
            assertThat(jdbc.queryForObject("SELECT status FROM family_alert_delivery WHERE event_id=(SELECT event_id FROM family_alert_event WHERE incident_id=?)", String.class, id)).isEqualTo("SKIPPED");
            assertThat(familyMessages()).isZero();
            assertThat(count("family_alert_window")).isZero();
        }
    }

    private Map<String, Object> input() {
        return Map.of("visitId", visit, "category", "SERVICE", "severity", "MEDIUM", "description", "Private source narrative",
                "expectedVersion", 0, "clientRequestId", UUID.randomUUID().toString());
    }
    private void removeManagers() { jdbc.update("DELETE FROM user_role WHERE role='MANAGER'"); }
    private List<String> eventTypes() { return jdbc.queryForList("SELECT event_type FROM family_alert_event WHERE elder_id=?", String.class, elder); }
    private long familyMessages() { return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE recipient_user_id=?", Long.class, family); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
}
