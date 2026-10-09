package sg.nus.carelink.incident.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.testsupport.SharedMySql;

/** Real EL03 HTTP acceptance at the SOS, family inbox/detail/receipt and commit boundaries.
 * SQL supplies synthetic identities/bindings and observes durable facts, never the source event.
 * @author Wang Zhili
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "carelink.report.schedule-cron=-", "carelink.roster.schedule-cron=-", "carelink.caregiver.expiry-scan-cron=-",
        "carelink.rerostering.scan-initial-delay=PT1H", "carelink.escalation.scan-initial-delay=PT1H",
        "carelink.roster.uncovered-scan-initial-delay=PT1H", "carelink.roster.leave-reminder-initial-delay=PT1H"
})
class FamilyElderSosWorkflowIT {
    private static final String SOS = "/api/elders/me/emergency-calls";
    private static final String DESCRIPTION = "Fictional elder needs help at home.";
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();
    private String managerName, elderName, familyAName, familyBName;
    private long elder, familyA, familyB, familyAUser, familyBUser;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, FamilyElderSosWorkflowIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
    }
    @BeforeEach void peopleOnly() {
        // The class owns a fresh SharedMySql database; prior managers must not take this scenario's incident.
        jdbc.update("DELETE FROM user_role WHERE role='MANAGER'");
        jdbc.update("UPDATE caregiver SET status='INACTIVE'");
        String run = UUID.randomUUID().toString().substring(0, 8);
        managerName = "fm05-manager-" + run;
        elderName = "fm05-elder-" + run; familyAName = "fm05-family-a-" + run; familyBName = "fm05-family-b-" + run;
        account(managerName, "MANAGER");
        elder = insert("INSERT INTO elder(user_id,full_name,sector,preferred_dialects) VALUES (?,'Fictional elder','North','English')", account(elderName, "ELDER"));
        familyAUser = account(familyAName, "FAMILY"); familyBUser = account(familyBName, "FAMILY");
        familyA = relative(familyAUser, "FULL"); familyB = relative(familyBUser, "READ_ONLY");
    }

    @Test void realSosWithoutVisitReachesBothFamiliesAndKeepsReadViewAwarenessIndependent() throws Exception {
        // EL03 uses real Singapore system time; a test Clock does not freeze reportedAt.
        Instant before = Instant.now();
        long id = report(Map.of("description", DESCRIPTION, "latitude", 1.2345678,
                "longitude", 103.1234567, "locationText", "Private fictional location"));
        Instant after = Instant.now();
        try (var a = browser(familyAName); var b = browser(familyBName); var manager = browser(managerName)) {
            var managerBefore = manager.read("/api/incidents/" + id);
            var noticeA = notice(a, id); var noticeB = notice(b, id);
            assertThat(noticeA.path("id").asLong()).isNotEqualTo(noticeB.path("id").asLong());
            assertThat(noticeA.path("eventType").asString()).isEqualTo("INCIDENT_RAISED");
            assertThat(noticeA.path("body").asString()).contains("SOS").doesNotContain(DESCRIPTION, "Private fictional location");
            var detailA = detail(a, id); var detailB = detail(b, id);
            assertThat(detailA.path("elderId").asLong()).isEqualTo(elder);
            assertThat(detailA.path("source").asString()).isEqualTo("ELDER_SOS");
            assertThat(detailA.path("category").asString()).isEqualTo("SOS");
            assertThat(detailA.path("severity").asString()).isEqualTo("HIGH");
            assertThat(detailA.path("visitId").isNull()).isTrue();
            assertThat(detailA.path("description").asString()).isEqualTo(DESCRIPTION);
            assertThat(detailA.propertyNames()).doesNotContain("latitude", "longitude", "locationText", "reportedByUserId", "responderUserId", "respondBy");
            assertThat(OffsetDateTime.parse(detailA.path("reportedAt").asString()).getOffset().getTotalSeconds()).isEqualTo(28800);
            // Existing incident DATETIME rounds to seconds; permit one second at both boundaries.
            assertThat(OffsetDateTime.parse(detailA.path("reportedAt").asString()).toInstant()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
            // The contractual two-hour window starts at message creation, not reportedAt or inbox fetch.
            assertThat(OffsetDateTime.parse(detailA.path("acknowledgeBy").asString()).toInstant()).isBetween(before.plusSeconds(7200), after.plusSeconds(7200));
            assertEmptyReceipt(detailA); assertEmptyReceipt(detailB);
            assertThat(detailA.path("acknowledgement").path("familyMemberId").asLong()).isEqualTo(familyA);
            assertThat(detailB.path("acknowledgement").path("familyMemberId").asLong()).isEqualTo(familyB);
            var read = body(a.command("POST", "/api/notifications/" + noticeA.path("id").asLong() + "/read", null), 200);
            assertThat(read.path("status").asString()).isEqualTo("READ"); assertEmptyReceipt(detail(a, id));
            var viewed = body(a.command("POST", "/api/incidents/" + id + "/view", null), 200);
            assertThat(viewed.path("viewedAt").isNull()).isFalse();
            assertThat(viewed.path("acknowledgedAt").isNull()).isTrue();
            var aware = body(a.command("POST", "/api/incidents/" + id + "/acknowledge", Map.of("responseNote", "A knows about SOS")), 200);
            assertThat(aware.path("acknowledgedAt").isNull()).isFalse();
            assertThat(aware.path("viewedAt")).isEqualTo(viewed.path("viewedAt"));
            assertThat(body(a.command("POST", "/api/incidents/" + id + "/acknowledge", Map.of("responseNote", "Do not overwrite")), 200)).isEqualTo(aware);
            assertThat(body(a.command("POST", "/api/incidents/" + id + "/view", null), 200)).isEqualTo(aware);
            assertThat(body(a.command("POST", "/api/notifications/" + noticeA.path("id").asLong() + "/read", null), 200)).isEqualTo(read);
            assertThat(detail(a, id).path("acknowledgeBy")).isEqualTo(detailA.path("acknowledgeBy"));
            assertEmptyReceipt(detail(b, id));
            assertThat(notice(b, id).path("status").asString()).isEqualTo("SENT");
            assertThat(b.read("/api/notifications/me/unread-count").path("unread").asInt()).isEqualTo(1);
            assertThat(a.read("/api/notifications/me/unread-count").path("unread").asInt()).isZero();
            assertThat(manager.read("/api/incidents/" + id)).isEqualTo(managerBefore);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE incident_id=?", Long.class, id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_window WHERE incident_id=?", Long.class, id)).isEqualTo(2);
        }
    }

    @Test void failureAfterEventRegistrationRollsBackSosAndAllNotificationEffects() throws Exception {
        // ASSIGNED is saved after raised-event registration; constrain the fault to this elder only.
        jdbc.execute("CREATE TRIGGER fm05_sos_source_fault BEFORE INSERT ON incident_log FOR EACH ROW BEGIN IF NEW.action='ASSIGNED' AND EXISTS (SELECT 1 FROM incident WHERE id=NEW.incident_id AND elder_id="
                + elder + ") THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic SOS source persistence failure'; END IF; END");
        try (var source = browser(elderName); var a = browser(familyAName); var b = browser(familyBName)) {
            assertThat(source.command("POST", SOS, Map.of()).statusCode()).isEqualTo(500);
            assertThat(a.read("/api/notifications/me").path("totalElements").asInt()).isZero();
            assertThat(b.read("/api/notifications/me").path("totalElements").asInt()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident WHERE elder_id=?", Long.class, elder)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE elder_id=?", Long.class, elder)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE recipient_user_id IN (?,?)", Long.class, familyAUser, familyBUser)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE recipient_user_id=(SELECT id FROM app_user WHERE username=?)", Long.class, managerName)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_window WHERE family_member_id IN (?,?)", Long.class, familyA, familyB)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_delivery WHERE family_member_id IN (?,?)", Long.class, familyA, familyB)).isZero();
        } finally { jdbc.execute("DROP TRIGGER fm05_sos_source_fault"); }
    }

    @Test void oneFamilyNotificationFailureKeepsCommittedSosAndDeliversToTheOtherFamily() throws Exception {
        jdbc.execute("CREATE TRIGGER fm05_sos_notice_fault BEFORE INSERT ON notification FOR EACH ROW BEGIN IF NEW.recipient_user_id="
                + familyAUser + " AND NEW.resource_type='INCIDENT' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic SOS recipient failure'; END IF; END");
        try {
            long id = report(Map.of());
            try (var a = browser(familyAName); var b = browser(familyBName); var manager = browser(managerName)) {
                assertThat(manager.read("/api/incidents/" + id).path("incident").path("source").asString()).isEqualTo("ELDER_SOS");
                assertThat(a.read("/api/notifications/me").path("totalElements").asInt()).isZero();
                assertThat(detail(a, id).path("acknowledgeBy").isNull()).isTrue();
                assertThat(notice(b, id).path("status").asString()).isEqualTo("SENT");
                assertThat(detail(b, id).path("acknowledgeBy").isNull()).isFalse();
                assertThat(body(b.command("POST", "/api/incidents/" + id + "/acknowledge", Map.of("responseNote", "B received SOS")), 200).path("acknowledgedAt").isNull()).isFalse();
                assertEmptyReceipt(detail(a, id));
                assertThat(jdbc.queryForObject("SELECT state FROM family_alert_event WHERE incident_id=?", String.class, id)).isEqualTo("FAILED");
                assertThat(jdbc.queryForObject("SELECT status FROM family_alert_delivery WHERE family_member_id=? AND event_id=(SELECT event_id FROM family_alert_event WHERE incident_id=?)", String.class, familyA, id)).isEqualTo("FAILED");
                assertThat(jdbc.queryForObject("SELECT status FROM family_alert_delivery WHERE family_member_id=? AND event_id=(SELECT event_id FROM family_alert_event WHERE incident_id=?)", String.class, familyB, id)).isEqualTo("CREATED");
            }
        } finally { jdbc.execute("DROP TRIGGER fm05_sos_notice_fault"); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void realBindingRevocationBeforeOrAfterSosExcludesFamilyAndPreservesAnotherRecipient(boolean afterSos) throws Exception {
        try (var source = browser(elderName); var a = browser(familyAName); var b = browser(familyBName)) {
            long id = 0, noticeId = 0;
            if (afterSos) {
                id = body(source.command("POST", SOS, Map.of()), 201).path("id").asLong();
                noticeId = notice(a, id).path("id").asLong();
                assertEmptyReceipt(detail(a, id));
            }
            long binding = source.read("/api/elders/me/family-bindings").valueStream()
                    .filter(row -> row.path("familyMemberId").asLong() == familyA).findFirst().orElseThrow().path("id").asLong();
            assertThat(body(source.command("DELETE", "/api/elders/me/family-bindings/" + binding, null), 200).path("status").asString()).isEqualTo("REVOKED");
            if (!afterSos) id = body(source.command("POST", SOS, Map.of()), 201).path("id").asLong();
            assertThat(a.get("/api/family/incidents/" + id).statusCode()).isEqualTo(403);
            assertThat(a.command("POST", "/api/incidents/" + id + "/view", null).statusCode()).isEqualTo(403);
            assertThat(a.command("POST", "/api/incidents/" + id + "/acknowledge", Map.of("responseNote", "Denied" )).statusCode()).isEqualTo(403);
            assertThat(a.read("/api/notifications/me").path("totalElements").asInt()).isZero();
            assertThat(a.read("/api/notifications/me/unread-count").path("unread").asInt()).isZero();
            assertThat(body(a.command("POST", "/api/notifications/me/read-all", null), 200).path("updated").asInt()).isZero();
            if (afterSos) {
                assertThat(a.command("POST", "/api/notifications/" + noticeId + "/read", null).statusCode()).isEqualTo(404);
                assertThat(jdbc.queryForObject("SELECT status FROM notification WHERE id=?", String.class, noticeId)).isEqualTo("SENT");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_window WHERE incident_id=? AND family_member_id=?", Long.class, id, familyA)).isEqualTo(1);
            } else {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE recipient_user_id=?", Long.class, familyAUser)).isZero();
                assertThat(jdbc.queryForObject("SELECT reason FROM family_alert_delivery WHERE family_member_id=? AND event_id=(SELECT event_id FROM family_alert_event WHERE incident_id=?)", String.class, familyA, id)).isEqualTo("BINDING_REVOKED");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_window WHERE incident_id=? AND family_member_id=?", Long.class, id, familyA)).isZero();
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident_acknowledgement WHERE incident_id=?", Long.class, id)).isZero();
            assertThat(notice(b, id).path("status").asString()).isEqualTo("SENT");
            assertThat(detail(b, id).path("visitId").isNull()).isTrue();
            assertThat(body(b.command("POST", "/api/incidents/" + id + "/acknowledge", Map.of("responseNote", "B remains authorized")), 200).path("acknowledgedAt").isNull()).isFalse();
        }
    }

    @Test void sosWithoutAvailableResponderCreatesTwoDifferentFactsAndOneWindowPerFamily() throws Exception {
        jdbc.update("DELETE FROM user_role WHERE role='MANAGER'");
        long id = report(null);
        try (var a = browser(familyAName); var b = browser(familyBName)) {
            var page = a.read("/api/notifications/me");
            assertThat(page.path("totalElements").asInt()).isEqualTo(2);
            assertThat(page.path("items").valueStream().map(row -> row.path("eventType").asString()).toList())
                    .containsExactlyInAnyOrder("INCIDENT_RAISED", "INCIDENT_UNRESOLVED");
            var original = detail(a, id);
            assertThat(original.path("status").asString()).isEqualTo("UNRESOLVED_ESCALATED");
            assertThat(original.path("description").isNull()).isTrue();
            assertEmptyReceipt(original);
            var aware = body(a.command("POST", "/api/incidents/" + id + "/acknowledge", Map.of("responseNote", "Aware; staff response still required")), 200);
            assertThat(aware.path("viewedAt").isNull()).isTrue();
            assertThat(detail(a, id).path("status")).isEqualTo(original.path("status"));
            assertThat(detail(a, id).path("acknowledgeBy")).isEqualTo(original.path("acknowledgeBy"));
            assertThat(a.read("/api/notifications/me/unread-count").path("unread").asInt()).isEqualTo(2);
            assertThat(b.read("/api/notifications/me").path("totalElements").asInt()).isEqualTo(2);
            assertEmptyReceipt(detail(b, id));
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT event_id) FROM family_alert_event WHERE incident_id=?", Long.class, id)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_window WHERE incident_id=?", Long.class, id)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_delivery WHERE event_id IN (SELECT event_id FROM family_alert_event WHERE incident_id=?) AND status='CREATED'", Long.class, id)).isEqualTo(4);
        }
    }

    @Test void twoSosPostsAreTwoNewIncidentsRatherThanAReplayOfTheSameCommand() throws Exception {
        // EL03 has no persisted clientRequestId; consumer deduplication must not merge distinct calls.
        long first = report(Map.of()); long second = report(Map.of());
        assertThat(second).isNotEqualTo(first);
        try (var a = browser(familyAName); var b = browser(familyBName)) {
            for (var reader : java.util.List.of(a, b)) {
                var page = reader.read("/api/notifications/me");
                assertThat(page.path("totalElements").asInt()).isEqualTo(2);
                assertThat(page.path("items").valueStream().map(row -> row.path("resourceId").asLong()).toList())
                        .containsExactlyInAnyOrder(first, second);
                assertEmptyReceipt(detail(reader, first)); assertEmptyReceipt(detail(reader, second));
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident WHERE elder_id=?", Long.class, elder)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE elder_id=?", Long.class, elder)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_window WHERE family_member_id IN (?,?)", Long.class, familyA, familyB)).isEqualTo(4);
        }
    }

    @Test void rejectedSourceRequestsNeverCreateAnIncidentOrFamilyEvent() throws Exception {
        String unlinked = "sos-no-profile-" + UUID.randomUUID(); account(unlinked, "ELDER");
        try (var anonymous = new Browser(); var source = browser(elderName);
                var wrongRole = browser(familyAName); var noProfile = browser(unlinked)) {
            assertThat(anonymous.command("POST", SOS, Map.of()).statusCode()).isEqualTo(401);
            assertThat(wrongRole.command("POST", SOS, Map.of()).statusCode()).isEqualTo(403);
            assertThat(noProfile.command("POST", SOS, Map.of()).statusCode()).isEqualTo(404);
            assertThat(source.command("POST", SOS, Map.of("latitude", 91)).statusCode()).isEqualTo(400);
            // A logged-in elder still needs CSRF; this intentionally bypasses the command helper.
            var missingCsrf = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + SOS))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            assertThat(source.client.send(missingCsrf, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
            assertThat(wrongRole.read("/api/notifications/me").path("totalElements").asInt()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident WHERE elder_id=?", Long.class, elder)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_event WHERE elder_id=?", Long.class, elder)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM family_alert_window WHERE family_member_id IN (?,?)", Long.class, familyA, familyB)).isZero();
        }
    }

    private long report(Map<String, ?> payload) throws Exception {
        try (var elder = browser(elderName)) { return body(elder.command("POST", SOS, payload), 201).path("id").asLong(); }
    }
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
