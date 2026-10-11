package sg.nus.carelink.incident.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.incident.application.EscalationService;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.testsupport.CaregiverHttpITSupport;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * Real CG04 reports feed FM05; no test publishes the family event. visit takes the caregiver's report
 * and hands it to core's internal API, which these tests call the way visit does. The command's
 * receipt, its replay and its rollback are visit's, and visit's own tests cover them.
 * @author Wang Zhili
 */
class FamilyIncidentSourceIT extends CaregiverHttpITSupport {
    @Autowired private EscalationService escalation;
    private CoreApi core;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, FamilyIncidentSourceIT.class, null);
    }

    @BeforeEach void clearPreviousScenarios() {
        // SharedMySql isolates the class; clear only its previous incident scenarios.
        for (String table : List.of("family_alert_window", "family_alert_delivery", "family_alert_event", "notification",
                "incident_acknowledgement", "incident_log", "incident")) {
            jdbc.update("DELETE FROM " + table);
        }
        core = internalApi();
    }

    @Test void committedReportCreatesOneSafeFamilyMessageAndPreservesStaffRouting() throws Exception {
        try (var familyBrowser = browser(familyName)) {
            long id = report().id();
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
            // What the caregiver sees of the report through visit
            assertThat(core.incident(id, caregiverUser).description()).isEqualTo("Private source narrative");
            assertThat(core.incidents(caregiverUser, visit, 0, 20).totalElements()).isEqualTo(1);
        }
    }

    @Test void initiallyEmptyChainPublishesTwoDifferentFactsWithoutLegacyDuplicates() {
        removeManagers();
        long id = report().id();
        assertThat(jdbc.queryForObject("SELECT status FROM incident WHERE id=?", String.class, id)).isEqualTo("UNRESOLVED_ESCALATED");
        assertThat(eventTypes()).containsExactlyInAnyOrder("INCIDENT_RAISED", "INCIDENT_UNRESOLVED");
        assertThat(familyMessages()).isEqualTo(2);
        assertThat(count("family_alert_delivery")).isEqualTo(2);
        assertThat(count("family_alert_window")).isEqualTo(1);
    }

    @Test void realOverdueEscalationPublishesUnresolvedOnceAndKeepsFirstWindow() {
        long id = report().id();
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

    @ParameterizedTest @ValueSource(strings = {"notification", "family_alert_event"})
    void observerStorageFailureDoesNotTurnCommittedReportIntoFailedResponse(String table) {
        String condition = table.equals("notification") ? "NEW.recipient_user_id=" + family : "NEW.elder_id=" + elder;
        jdbc.execute("CREATE TRIGGER fm05_source_fault BEFORE INSERT ON " + table + " FOR EACH ROW BEGIN IF "
                + condition + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Forced family storage failure'; END IF; END");
        try {
            long id = report().id();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident WHERE visit_id=?", Long.class, visit)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE resource_id=? AND recipient_user_id=?", Long.class, id, manager)).isGreaterThan(0);
            assertThat(familyMessages()).isZero();
            assertThat(count("family_alert_window")).isZero();
            if (table.equals("notification")) {
                assertThat(jdbc.queryForObject("SELECT state FROM family_alert_event WHERE incident_id=?", String.class, id)).isEqualTo("FAILED");
                assertThat(jdbc.queryForObject("SELECT status FROM family_alert_delivery WHERE event_id=(SELECT event_id FROM family_alert_event WHERE incident_id=?)", String.class, id)).isEqualTo("FAILED");
            }
        } finally { jdbc.execute("DROP TRIGGER fm05_source_fault"); }
    }

    @Test void revokedRecipientIsRecordedInsteadOfReceivingLegacyMessage() {
        jdbc.update("UPDATE elder_family_binding SET status='REVOKED' WHERE elder_id=?", elder);
        long id = report().id();
        assertThat(jdbc.queryForObject("SELECT state FROM family_alert_event WHERE incident_id=?", String.class, id)).isEqualTo("NO_RECIPIENTS");
        assertThat(jdbc.queryForObject("SELECT status FROM family_alert_delivery WHERE event_id=(SELECT event_id FROM family_alert_event WHERE incident_id=?)", String.class, id)).isEqualTo("SKIPPED");
        assertThat(familyMessages()).isZero();
        assertThat(count("family_alert_window")).isZero();
    }

    /** What visit sends core once the caregiver's report is in: the elder, the visit and who reported it. */
    private CoreApi.IncidentReport report() {
        return core.reportIncident(new CoreApi.CaregiverIncidentRequest(elder, visit, caregiverUser, "SERVICE", "MEDIUM",
                "Private source narrative"));
    }
    private void removeManagers() { jdbc.update("DELETE FROM user_role WHERE role='MANAGER'"); }
    private List<String> eventTypes() { return jdbc.queryForList("SELECT event_type FROM family_alert_event WHERE elder_id=?", String.class, elder); }
    private long familyMessages() { return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE recipient_user_id=?", Long.class, family); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
}
