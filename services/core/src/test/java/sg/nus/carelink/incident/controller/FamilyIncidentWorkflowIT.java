package sg.nus.carelink.incident.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.testsupport.SharedMySql;

/** HTTP acceptance from published/assigned care to each family's independent receipts.
 * SQL supplies synthetic identities/bindings and observes durable facts, never the source event.
 * @author Wang Zhili
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "carelink.report.schedule-cron=-", "carelink.roster.schedule-cron=-", "carelink.caregiver.expiry-scan-cron=-",
        "carelink.rerostering.scan-initial-delay=PT1H", "carelink.escalation.scan-initial-delay=PT1H",
        "carelink.roster.uncovered-scan-initial-delay=PT1H", "carelink.roster.leave-reminder-initial-delay=PT1H"
})
@Import(FamilyIncidentWorkflowIT.TimeConfiguration.class)
class FamilyIncidentWorkflowIT {
    private static final Instant START = Instant.parse("2026-10-08T02:00:00Z");
    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
    private static final String DESCRIPTION = "A fictional fall during care; assistance was provided.";
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ScenarioClock clock;
    private final JsonMapper json = JsonMapper.builder().build();
    private String managerName, caregiverName, elderName, familyAName, familyBName;
    private long elder, caregiver, familyA, familyB, familyAUser, familyBUser;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, FamilyIncidentWorkflowIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
    }
    @TestConfiguration(proxyBeanMethods = false) static class TimeConfiguration {
        @Bean @Primary ScenarioClock workflowClock() { return new ScenarioClock(); }
    }
    static class ScenarioClock extends Clock {
        private volatile Instant current = START;
        void at(Instant value) { current = value; }
        @Override public ZoneId getZone() { return SINGAPORE; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(current, zone); }
        @Override public Instant instant() { return current; }
    }

    @BeforeEach void peopleOnly() {
        clock.at(START);
        // The class owns a fresh SharedMySql database; prior managers must not take this scenario's incident.
        jdbc.update("DELETE FROM user_role WHERE role='MANAGER'");
        jdbc.update("UPDATE caregiver SET status='INACTIVE'");
        String run = UUID.randomUUID().toString().substring(0, 8);
        managerName = "fm05-manager-" + run; caregiverName = "fm05-caregiver-" + run;
        elderName = "fm05-elder-" + run; familyAName = "fm05-family-a-" + run; familyBName = "fm05-family-b-" + run;
        account(managerName, "MANAGER");
        caregiver = insert("INSERT INTO caregiver(user_id,full_name,status,sector,dialects) VALUES (?,'Fictional caregiver','AVAILABLE','North','English')", account(caregiverName, "CAREGIVER"));
        elder = insert("INSERT INTO elder(user_id,full_name,sector,preferred_dialects) VALUES (?,'Fictional elder','North','English')", account(elderName, "ELDER"));
        familyAUser = account(familyAName, "FAMILY"); familyBUser = account(familyBName, "FAMILY");
        familyA = relative(familyAUser, "FULL"); familyB = relative(familyBUser, "READ_ONLY");
    }

    @Test void publishedAssignedCareReachesTwoInboxesAndReadViewAwarenessStayIndependent() throws Exception {
        var reported = report();
        try (var a = browser(familyAName); var b = browser(familyBName); var manager = browser(managerName)) {
            var managerBefore = manager.read("/api/incidents/" + reported.id());
            var noticeA = notice(a, reported.id()); var noticeB = notice(b, reported.id());
            assertThat(noticeA.path("id").asLong()).isNotEqualTo(noticeB.path("id").asLong());
            assertThat(noticeA.path("status").asString()).isEqualTo("SENT");
            assertThat(noticeA.path("title").asString()).isEqualTo("Urgent care alert: HIGH");
            assertThat(noticeA.path("body").asString()).contains("FALL").doesNotContain(DESCRIPTION);
            var detailA = detail(a, reported.id()); var detailB = detail(b, reported.id());
            assertThat(detailA.path("description").asString()).isEqualTo(DESCRIPTION);
            assertThat(detailA.path("visitId").asLong()).isEqualTo(reported.visit());
            assertThat(detailA.path("acknowledgeBy").asString()).isEqualTo("2026-10-08T12:05:00+08:00");
            assertThat(detailA.path("acknowledgement").path("familyMemberId").asLong()).isEqualTo(familyA);
            assertThat(detailB.path("acknowledgement").path("familyMemberId").asLong()).isEqualTo(familyB);
            assertEmptyReceipt(detailA); assertEmptyReceipt(detailB);
            assertThat(a.read("/api/notifications/me/unread-count").path("unread").asInt()).isEqualTo(1);
            clock.at(START.plusSeconds(360));
            var read = body(a.command("POST", "/api/notifications/" + noticeA.path("id").asLong() + "/read", null), 200);
            assertThat(read.path("status").asString()).isEqualTo("READ"); assertEmptyReceipt(detail(a, reported.id()));
            clock.at(START.plusSeconds(420));
            var viewed = body(a.command("POST", "/api/incidents/" + reported.id() + "/view", null), 200);
            assertThat(viewed.path("viewedAt").asString()).isEqualTo("2026-10-08T10:07:00+08:00");
            assertThat(viewed.path("acknowledgedAt").isNull()).isTrue();
            clock.at(START.plusSeconds(480));
            var aware = body(a.command("POST", "/api/incidents/" + reported.id() + "/acknowledge", Map.of("responseNote", "Family A knows")), 200);
            assertThat(aware.path("acknowledgedAt").asString()).isEqualTo("2026-10-08T10:08:00+08:00");
            assertThat(aware.path("viewedAt")).isEqualTo(viewed.path("viewedAt"));
            clock.at(START.plusSeconds(540));
            assertThat(body(a.command("POST", "/api/incidents/" + reported.id() + "/acknowledge", Map.of("responseNote", "Must not replace first note")), 200)).isEqualTo(aware);
            assertThat(body(a.command("POST", "/api/incidents/" + reported.id() + "/view", null), 200)).isEqualTo(aware);
            assertThat(body(a.command("POST", "/api/notifications/" + noticeA.path("id").asLong() + "/read", null), 200)).isEqualTo(read);
            assertThat(detail(a, reported.id()).path("acknowledgeBy")).isEqualTo(detailA.path("acknowledgeBy"));
            assertEmptyReceipt(detail(b, reported.id()));
            assertThat(notice(b, reported.id()).path("status").asString()).isEqualTo("SENT");
            assertThat(b.read("/api/notifications/me/unread-count").path("unread").asInt()).isEqualTo(1);
            assertThat(a.read("/api/notifications/me/unread-count").path("unread").asInt()).isZero();
            assertThat(manager.read("/api/incidents/" + reported.id())).isEqualTo(managerBefore);
            assertThat(a.read("/api/visits/" + reported.visit()).path("status").asString()).isEqualTo("EXCEPTION");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE incident_id=?", Long.class, reported.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_window WHERE incident_id=?", Long.class, reported.id())).isEqualTo(2);
        }
    }

    @Test void concurrentCg04CommandReplayCreatesOneFactAndOneMessagePerFamily() throws Exception {
        long visit = plannedVisit(); var input = reportInput(visit);
        try (var first = browser(caregiverName); var second = browser(caregiverName);
                var a = browser(familyAName); var b = browser(familyBName)) {
            var pending = commandAsync(first, "/api/incidents", input);
            var response = second.command("POST", "/api/incidents", input);
            var earlier = pending.get();
            assertThat(List.of(earlier.statusCode(), response.statusCode())).containsExactlyInAnyOrder(201, 200);
            long id = json.readTree(response.body()).path("report").path("id").asLong();
            assertThat(json.readTree(earlier.body()).path("report").path("id").asLong()).isEqualTo(id);
            var noticeA = notice(a, id); var noticeB = notice(b, id);
            var deadline = detail(a, id).path("acknowledgeBy");
            clock.at(START.plusSeconds(600));
            assertThat(body(first.command("POST", "/api/incidents", input), 200).path("replayed").asBoolean()).isTrue();
            assertThat(notice(a, id)).isEqualTo(noticeA); assertThat(notice(b, id)).isEqualTo(noticeB);
            assertThat(detail(a, id).path("acknowledgeBy")).isEqualTo(deadline);
            assertEmptyReceipt(detail(a, id)); assertEmptyReceipt(detail(b, id));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE incident_id=?", Long.class, id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_window WHERE incident_id=?", Long.class, id)).isEqualTo(2);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"view", "acknowledge"})
    void concurrentFamilyCommandsPreserveFirstAwarenessAndDoNotAffectAnotherRecipient(String action) throws Exception {
        var report = report();
        try (var first = browser(familyAName); var second = browser(familyAName); var other = browser(familyBName)) {
            var pending = commandAsync(second, "/api/incidents/" + report.id() + "/" + action,
                    action.equals("view") ? null : Map.of("responseNote", "Concurrent second note"));
            var response = body(first.command("POST", "/api/incidents/" + report.id() + "/acknowledge", Map.of("responseNote", "Concurrent first note")), 200);
            var competing = body(pending.get(), 200);
            var saved = detail(first, report.id()).path("acknowledgement");
            assertThat(saved.path("acknowledgedAt").isNull()).isFalse();
            assertThat(saved.path("viewedAt").isNull()).isEqualTo(!action.equals("view"));
            assertThat(saved.path("responseNote").asString()).isIn("Concurrent first note", "Concurrent second note");
            if (action.equals("acknowledge")) { assertThat(response).isEqualTo(saved); assertThat(competing).isEqualTo(saved); }
            clock.at(START.plusSeconds(600));
            assertThat(body(first.command("POST", "/api/incidents/" + report.id() + "/acknowledge", Map.of("responseNote", "Later overwrite rejected")), 200)).isEqualTo(saved);
            assertEmptyReceipt(detail(other, report.id()));
            assertThat(notice(first, report.id()).path("status").asString()).isEqualTo("SENT");
            assertThat(notice(other, report.id()).path("status").asString()).isEqualTo("SENT");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void lateAwarenessRemainsLegalWithoutReadingAndDoesNotChangeOpenOrResolvedIncident(boolean resolved) throws Exception {
        var report = report();
        try (var family = browser(familyBName); var manager = browser(managerName)) {
            var original = detail(family, report.id());
            if (resolved) {
                body(manager.command("POST", "/api/incidents/" + report.id() + "/claim", null), 200);
                body(manager.command("POST", "/api/incidents/" + report.id() + "/resolve", Map.of("resolutionNote", "Private manager handling", "outcome", "HANDLED_ON_SITE")), 200);
            }
            var staffBefore = manager.read("/api/incidents/" + report.id());
            clock.at(START.plusSeconds(3 * 3600));
            var afterDeadline = detail(family, report.id());
            assertThat(afterDeadline.path("status").asString()).isEqualTo(resolved ? "RESOLVED" : "OPEN");
            assertThat(afterDeadline.path("acknowledgeBy")).isEqualTo(original.path("acknowledgeBy"));
            assertThat(afterDeadline.toString()).doesNotContain("Private manager handling", "responderUserId", "timeline");
            assertEmptyReceipt(afterDeadline);
            var receipt = body(family.command("POST", "/api/incidents/" + report.id() + "/acknowledge", Map.of("responseNote", "Late awareness")), 200);
            assertThat(receipt.path("acknowledgedAt").asString()).isEqualTo("2026-10-08T13:00:00+08:00");
            assertThat(receipt.path("viewedAt").isNull()).isTrue();
            assertThat(notice(family, report.id()).path("status").asString()).isEqualTo("SENT");
            assertThat(detail(family, report.id()).path("acknowledgeBy")).isEqualTo(original.path("acknowledgeBy"));
            assertThat(manager.read("/api/incidents/" + report.id())).isEqualTo(staffBefore);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"revoke", "expire"})
    void bindingLossAfterInboxFetchRejectsAllCareReadsAndWritesWithoutDeletingHistory(String change) throws Exception {
        var report = report();
        try (var a = browser(familyAName); var b = browser(familyBName); var elderBrowser = browser(elderName)) {
            if (change.equals("expire")) jdbc.update("UPDATE elder_family_binding SET expires_at=? WHERE elder_id=? AND family_member_id=?",
                    java.sql.Timestamp.valueOf("2026-10-08 10:06:00"), elder, familyA);
            var notice = notice(a, report.id()); var original = detail(a, report.id());
            var messages = jdbc.queryForList("SELECT * FROM notification WHERE id=?", notice.path("id").asLong());
            var windows = jdbc.queryForList("SELECT * FROM family_alert_window WHERE incident_id=?", report.id());
            if (change.equals("revoke")) {
                long binding = 0;
                for (var row : elderBrowser.read("/api/elders/me/family-bindings")) {
                    if (row.path("familyMemberId").asLong() == familyA) binding = row.path("id").asLong();
                }
                assertThat(binding).isPositive();
                assertThat(body(elderBrowser.command("DELETE", "/api/elders/me/family-bindings/" + binding, null), 200).path("status").asString()).isEqualTo("REVOKED");
            } else clock.at(START.plusSeconds(360));
            assertThat(a.get("/api/family/incidents/" + report.id()).statusCode()).isEqualTo(403);
            assertThat(a.command("POST", "/api/incidents/" + report.id() + "/view", null).statusCode()).isEqualTo(403);
            assertThat(a.command("POST", "/api/incidents/" + report.id() + "/acknowledge", Map.of("responseNote", "Not allowed" )).statusCode()).isEqualTo(403);
            assertThat(a.command("POST", "/api/notifications/" + notice.path("id").asLong() + "/read", null).statusCode()).isEqualTo(404);
            assertThat(a.read("/api/notifications/me").path("totalElements").asInt()).isZero();
            assertThat(a.read("/api/notifications/me/unread-count").path("unread").asInt()).isZero();
            assertThat(body(a.command("POST", "/api/notifications/me/read-all", null), 200).path("updated").asInt()).isZero();
            assertThat(jdbc.queryForList("SELECT * FROM notification WHERE id=?", notice.path("id").asLong())).isEqualTo(messages);
            assertThat(jdbc.queryForList("SELECT * FROM family_alert_window WHERE incident_id=?", report.id())).isEqualTo(windows);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident_acknowledgement WHERE incident_id=?", Long.class, report.id())).isZero();
            assertThat(detail(b, report.id()).path("acknowledgeBy")).isEqualTo(original.path("acknowledgeBy"));
            assertThat(notice(b, report.id()).path("status").asString()).isEqualTo("SENT");
        }
    }

    @Test void failureSavingCg04ReceiptRollsBackReportVisitAndAllNotificationEffects() throws Exception {
        long visit = plannedVisit();
        // Database-boundary failure occurs after routing but before the caregiver source commits.
        jdbc.execute("CREATE TRIGGER fm05_workflow_source_fault BEFORE INSERT ON caregiver_command_receipt FOR EACH ROW BEGIN IF NEW.visit_id="
                + visit + " AND NEW.action_code='REPORT_INCIDENT' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic source persistence failure'; END IF; END");
        try (var caregiver = browser(caregiverName); var family = browser(familyAName)) {
            assertThat(caregiver.command("POST", "/api/incidents", reportInput(visit)).statusCode()).isEqualTo(503);
            var pack = caregiver.read("/api/visits/" + visit + "/work-pack");
            assertThat(pack.path("visit").path("status").asString()).isEqualTo("SCHEDULED");
            assertThat(pack.path("visit").path("version").asInt()).isZero();
            assertThat(caregiver.read("/api/caregivers/me/incidents?visitId=" + visit).path("totalElements").asInt()).isZero();
            assertThat(family.read("/api/notifications/me").path("totalElements").asInt()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE elder_id=?", Long.class, elder)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident WHERE visit_id=?", Long.class, visit)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM caregiver_command_receipt WHERE visit_id=?", Long.class, visit)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_state_transition WHERE visit_id=? AND result='APPLIED'", Long.class, visit)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE recipient_user_id IN (?,?)", Long.class, familyAUser, familyBUser)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_window WHERE family_member_id IN (?,?)", Long.class, familyA, familyB)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_delivery WHERE recipient_user_id IN (?,?)", Long.class, familyAUser, familyBUser)).isZero();
        } finally { jdbc.execute("DROP TRIGGER fm05_workflow_source_fault"); }
    }

    @Test void oneFamilyStorageFailureDoesNotUndoCommittedReportOrBlockTheOtherFamily() throws Exception {
        jdbc.execute("CREATE TRIGGER fm05_workflow_notice_fault BEFORE INSERT ON notification FOR EACH ROW BEGIN IF NEW.recipient_user_id="
                + familyAUser + " AND NEW.resource_type='INCIDENT' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic recipient persistence failure'; END IF; END");
        try {
            var report = report();
            try (var a = browser(familyAName); var b = browser(familyBName); var caregiver = browser(caregiverName)) {
                assertThat(a.read("/api/notifications/me").path("totalElements").asInt()).isZero();
                assertThat(detail(a, report.id()).path("acknowledgeBy").isNull()).isTrue();
                var delivered = notice(b, report.id()); var deadline = detail(b, report.id()).path("acknowledgeBy");
                assertThat(deadline.asString()).isEqualTo("2026-10-08T12:05:00+08:00");
                assertThat(jdbc.queryForObject("SELECT state FROM family_alert_event WHERE incident_id=?", String.class, report.id())).isEqualTo("FAILED");
                assertThat(jdbc.queryForObject("SELECT status FROM family_alert_delivery WHERE family_member_id=? AND event_id=(SELECT event_id FROM family_alert_event WHERE incident_id=?)", String.class, familyA, report.id())).isEqualTo("FAILED");
                assertThat(jdbc.queryForObject("SELECT status FROM family_alert_delivery WHERE family_member_id=? AND event_id=(SELECT event_id FROM family_alert_event WHERE incident_id=?)", String.class, familyB, report.id())).isEqualTo("CREATED");
                assertThat(caregiver.read("/api/visits/" + report.visit() + "/work-pack").path("visit").path("status").asString()).isEqualTo("EXCEPTION");
                assertThat(body(caregiver.command("POST", "/api/incidents", report.input()), 200).path("replayed").asBoolean()).isTrue();
                assertThat(a.read("/api/notifications/me").path("totalElements").asInt()).isZero();
                assertThat(notice(b, report.id())).isEqualTo(delivered);
                assertThat(detail(b, report.id()).path("acknowledgeBy")).isEqualTo(deadline);
                assertThat(body(b.command("POST", "/api/incidents/" + report.id() + "/acknowledge", Map.of("responseNote", "Independent recipient can respond")), 200).path("acknowledgedAt").isNull()).isFalse();
            }
        } finally { jdbc.execute("DROP TRIGGER fm05_workflow_notice_fault"); }
    }

    @ParameterizedTest @ValueSource(strings = {"read", "acknowledge"})
    void failedRecipientWriteKeepsIndependentFactsAndOnlyExplicitRetrySavesSuccess(String action) throws Exception {
        var report = report();
        try (var family = browser(familyAName)) {
            var notice = notice(family, report.id()); var deadline = detail(family, report.id()).path("acknowledgeBy");
            var viewed = body(family.command("POST", "/api/incidents/" + report.id() + "/view", null), 200);
            String table = action.equals("read") ? "notification" : "incident_acknowledgement";
            String condition = action.equals("read") ? "NEW.id=" + notice.path("id").asLong() + " AND NEW.status='READ'"
                    : "NEW.incident_id=" + report.id() + " AND NEW.family_member_id=" + familyA + " AND NEW.acknowledged_at IS NOT NULL";
            String path = action.equals("read") ? "/api/notifications/" + notice.path("id").asLong() + "/read"
                    : "/api/incidents/" + report.id() + "/acknowledge";
            Map<String, Object> payload = action.equals("read") ? null : Map.of("responseNote", "Explicit retry note");
            jdbc.execute("CREATE TRIGGER fm05_workflow_write_fault BEFORE UPDATE ON " + table
                    + " FOR EACH ROW BEGIN IF " + condition + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic recipient write failure'; END IF; END");
            try {
                assertThat(family.command("POST", path, payload).statusCode()).isEqualTo(500);
                assertThat(notice(family, report.id()).path("status").asString()).isEqualTo("SENT");
                assertThat(notice(family, report.id()).path("readAt").isNull()).isTrue();
                assertThat(detail(family, report.id()).path("acknowledgement")).isEqualTo(viewed);
                assertThat(detail(family, report.id()).path("acknowledgeBy")).isEqualTo(deadline);
            } finally { jdbc.execute("DROP TRIGGER fm05_workflow_write_fault"); }
            clock.at(START.plusSeconds(360));
            var saved = body(family.command("POST", path, payload), 200);
            if (action.equals("read")) {
                assertThat(saved.path("readAt").asString()).isEqualTo("2026-10-08T10:06:00+08:00");
                assertThat(detail(family, report.id()).path("acknowledgement")).isEqualTo(viewed);
            } else {
                assertThat(saved.path("acknowledgedAt").asString()).isEqualTo("2026-10-08T10:06:00+08:00");
                assertThat(saved.path("viewedAt")).isEqualTo(viewed.path("viewedAt"));
                assertThat(notice(family, report.id()).path("status").asString()).isEqualTo("SENT");
            }
            clock.at(START.plusSeconds(420));
            assertThat(body(family.command("POST", path, payload), 200)).isEqualTo(saved);
            assertThat(detail(family, report.id()).path("acknowledgeBy")).isEqualTo(deadline);
        }
    }

    @Test void realChainExhaustionAddsADifferentMessageWithoutResettingWindowOrAwareness() throws Exception {
        var report = report();
        try (var family = browser(familyAName); var other = browser(familyBName); var manager = browser(managerName)) {
            var raised = notice(family, report.id()); var deadline = detail(family, report.id()).path("acknowledgeBy");
            var read = body(family.command("POST", "/api/notifications/" + raised.path("id").asLong() + "/read", null), 200);
            var awareness = body(family.command("POST", "/api/incidents/" + report.id() + "/acknowledge", Map.of("responseNote", "Already informed")), 200);
            clock.at(START.plusSeconds(360));
            assertThat(body(manager.command("POST", "/api/incidents/" + report.id() + "/escalate", Map.of("reason", "Synthetic chain exhausted")), 200).path("status").asString()).isEqualTo("UNRESOLVED_ESCALATED");
            var inbox = family.read("/api/notifications/me");
            assertThat(inbox.path("totalElements").asInt()).isEqualTo(2);
            assertThat(inbox.path("items").valueStream().map(row -> row.path("eventType").asString()).toList()).containsExactlyInAnyOrder("INCIDENT_RAISED", "INCIDENT_UNRESOLVED");
            assertThat(inbox.path("items").valueStream().filter(row -> row.path("id").asLong() == raised.path("id").asLong()).findFirst().orElseThrow()).isEqualTo(read);
            assertThat(family.read("/api/notifications/me/unread-count").path("unread").asInt()).isEqualTo(1);
            assertThat(other.read("/api/notifications/me").path("totalElements").asInt()).isEqualTo(2);
            assertThat(detail(family, report.id()).path("acknowledgement")).isEqualTo(awareness);
            assertThat(detail(family, report.id()).path("acknowledgeBy")).isEqualTo(deadline);
            assertEmptyReceipt(detail(other, report.id()));
            assertThat(manager.command("POST", "/api/incidents/" + report.id() + "/escalate", Map.of("reason", "Must not republish" )).statusCode()).isEqualTo(409);
            assertThat(family.read("/api/notifications/me").path("totalElements").asInt()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE incident_id=?", Long.class, report.id())).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_window WHERE incident_id=?", Long.class, report.id())).isEqualTo(2);
        }
    }

    private long plannedVisit() throws Exception {
        var start = LocalDateTime.ofInstant(START.plusSeconds(300), SINGAPORE);
        try (var manager = browser(managerName)) {
            body(manager.command("PUT", "/api/elders/" + elder + "/primary-caregiver", Map.of("caregiverId", caregiver)), 200);
            long plan = body(manager.command("POST", "/api/care-plans", Map.of("elderId", elder)), 201).path("id").asLong();
            body(manager.command("POST", "/api/care-plans/" + plan + "/publish", Map.of("startDate", start.toLocalDate().toString(), "nodes", List.of(Map.of(
                    "groupName", "Personal care", "name", "FM05 workflow care", "evidenceType", "CHECKLIST", "visits", List.of(Map.of("day", "THURSDAY", "startTime", "10:05", "minutes", 60)))))), 200);
            // Publishing also tells the family the plan is ready; these tests count incident messages only.
            jdbc.update("DELETE FROM notification WHERE resource_type = 'CARE_PLAN'");
            for (var row : manager.read("/api/visits/roster?date=2026-10-08")) {
                if (row.path("carePlanId").asLong() == plan) {
                    assertThat(row.path("caregiverId").asLong()).isEqualTo(caregiver);
                    clock.at(START.plusSeconds(300));
                    return row.path("id").asLong();
                }
            }
            throw new AssertionError("Published/assigned plan must produce its visit through the roster API");
        }
    }
    private Report report() throws Exception {
        long visit = plannedVisit();
        var input = reportInput(visit);
        try (var caregiver = browser(caregiverName)) {
            long id = body(caregiver.command("POST", "/api/incidents", input), 201).path("report").path("id").asLong();
            return new Report(id, visit, input);
        }
    }
    private Map<String, Object> reportInput(long visit) {
        return Map.of("visitId", visit, "expectedVersion", 0, "clientRequestId", UUID.randomUUID().toString(),
                "category", "FALL", "severity", "HIGH", "description", DESCRIPTION);
    }
    private CompletableFuture<HttpResponse<String>> commandAsync(Browser browser, String path, Map<String, ?> payload) {
        return CompletableFuture.supplyAsync(() -> {
            try { return browser.command("POST", path, payload); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        });
    }
    private record Report(long id, long visit, Map<String, Object> input) { }
    private JsonNode detail(Browser browser, long id) throws Exception { return browser.read("/api/family/incidents/" + id); }
    private JsonNode notice(Browser browser, long id) throws Exception {
        var page = browser.read("/api/notifications/me");
        assertThat(page.path("totalElements").asInt()).isEqualTo(1);
        var notice = page.path("items").get(0);
        assertThat(notice.path("resourceType").asString()).isEqualTo("INCIDENT");
        assertThat(notice.path("resourceId").asLong()).isEqualTo(id);
        return notice;
    }
    private void assertEmptyReceipt(JsonNode detail) {
        var receipt = detail.path("acknowledgement");
        assertThat(receipt.path("viewedAt").isNull()).isTrue(); assertThat(receipt.path("acknowledgedAt").isNull()).isTrue();
    }
    private long relative(long user, String scope) {
        long id = insert("INSERT INTO family_member(user_id,full_name) VALUES (?, 'Fictional family')", user);
        jdbc.update("INSERT INTO elder_family_binding(elder_id,family_member_id,relationship,access_scope,status) VALUES (?,?,'DAUGHTER',?,'ACTIVE')", elder, id, scope);
        return id;
    }
    private long account(String name, String role) {
        long id = insert("INSERT INTO app_user(username,password_hash,display_name) VALUES (?, '{noop}test-password', ?)", name, name);
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,?)", id, role); return id;
    }
    private long insert(String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key); return key.getKey().longValue();
    }
    private JsonNode body(HttpResponse<String> response, int expected) {
        assertThat(response.statusCode()).as("HTTP body: %s", response.body()).isEqualTo(expected); return json.readTree(response.body());
    }
    private Browser browser(String name) throws Exception {
        var browser = new Browser(); body(browser.command("POST", "/api/auth/login", Map.of("username", name, "password", "test-password")), 200); return browser;
    }
    private class Browser implements AutoCloseable {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path) throws Exception { return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString()); }
        JsonNode read(String path) throws Exception { return body(get(path), 200); }
        HttpResponse<String> command(String method, String path, Map<String, ?> payload) throws Exception {
            assertThat(get("/api/auth/csrf").statusCode()).isEqualTo(200);
            String token = cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue();
            var request = request(path).header("X-XSRF-TOKEN", token);
            var data = HttpRequest.BodyPublishers.noBody();
            if (payload != null) { request.header("Content-Type", "application/json"); data = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)); }
            return client.send(request.method(method, data).build(), HttpResponse.BodyHandlers.ofString());
        }
        private HttpRequest.Builder request(String path) { return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15)); }
        @Override public void close() { client.close(); }
    }
}
