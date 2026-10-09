package sg.nus.carelink.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Real browser sessions for caregiver handoff acceptance; SQL prepares synthetic people and visits only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "carelink.report.schedule-cron=-", "carelink.caregiver.expiry-scan-cron=-",
        "carelink.rerostering.scan-initial-delay=PT1H", "carelink.escalation.scan-initial-delay=PT1H",
        "carelink.roster.uncovered-scan-initial-delay=PT1H"
})
public abstract class CaregiverHttpITSupport {
    @LocalServerPort private int port;
    @Autowired protected JdbcTemplate jdbc;
    protected final JsonMapper json = JsonMapper.builder().build();
    protected String managerName, familyName, caregiverName, otherName;
    protected long manager, family, caregiverUser, caregiver, otherCaregiver, elder, visit;
    protected LocalDate day;

    @BeforeEach
    void preparePeopleAndScheduledVisit() {
        // Each test owns a private database; previous scenarios must not supply replacement candidates.
        jdbc.update("update caregiver set status='INACTIVE'");
        String run = UUID.randomUUID().toString().substring(0, 8);
        managerName = "manager-" + run; familyName = "family-" + run;
        caregiverName = "caregiver-" + run; otherName = "other-" + run;
        manager = account(managerName, "MANAGER"); family = account(familyName, "FAMILY");
        caregiverUser = account(caregiverName, "CAREGIVER");
        caregiver = person(caregiverUser, "Caregiver A");
        otherCaregiver = person(account(otherName, "CAREGIVER"), "Caregiver B");
        elder = insert("insert into elder(full_name,sector,preferred_dialects) values ('Demo Elder','North','English')");
        long familyProfile = insert("insert into family_member(user_id,full_name) values (?, 'Demo Family')", family);
        jdbc.update("insert into elder_family_binding(elder_id,family_member_id,relationship,access_scope,status) "
                + "values (?,?,'DAUGHTER','FULL','ACTIVE')", elder, familyProfile);
        day = LocalDate.now(ZoneId.of("Asia/Singapore")).plusDays(3);
        visit = insert("insert into visit(elder_id,caregiver_id,service_type,scheduled_start,scheduled_end,status) "
                + "values (?,?,'PERSONAL_CARE',?,?,'SCHEDULED')", elder, caregiver,
                Timestamp.valueOf(day.atTime(9, 0)), Timestamp.valueOf(day.atTime(10, 0)));
    }

    protected long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private long account(String name, String role) {
        long id = insert("insert into app_user(username,password_hash,display_name,enabled) values (?, '{noop}test-password', ?, true)", name, name);
        jdbc.update("insert into user_role(user_id,role) values (?,?)", id, role);
        return id;
    }

    private long person(long user, String name) {
        return insert("insert into caregiver(user_id,full_name,status,sector,dialects) values (?,?,'AVAILABLE','North','English')", user, name);
    }

    protected Browser browser(String name) throws Exception {
        Browser browser = new Browser();
        var response = browser.post("/api/auth/login", Map.of("username", name, "password", "test-password"));
        assertThat(response.statusCode()).as("login: %s", response.body()).isEqualTo(200);
        return browser;
    }

    protected JsonNode body(HttpResponse<String> response, int expected) {
        assertThat(response.statusCode()).as("HTTP body: %s", response.body()).isEqualTo(expected);
        return json.readTree(response.body());
    }

    protected class Browser implements AutoCloseable {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1).build();

        public HttpResponse<String> get(String path) throws Exception {
            return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        public JsonNode read(String path) throws Exception { return body(get(path), 200); }
        public HttpResponse<String> post(String path, Map<String, ?> payload) throws Exception {
            assertThat(get("/api/auth/csrf").statusCode()).isEqualTo(200);
            String csrf = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                    .findFirst().orElseThrow().getValue();
            return sendPost(path, payload, csrf);
        }
        public HttpResponse<String> withoutCsrf(String path, Map<String, ?> payload) throws Exception {
            return sendPost(path, payload, null);
        }
        public HttpResponse<String> put(String path, Map<String, ?> payload) throws Exception {
            assertThat(get("/api/auth/csrf").statusCode()).isEqualTo(200);
            String csrf = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN")).findFirst().orElseThrow().getValue();
            return client.send(request(path).header("Content-Type","application/json").header("X-XSRF-TOKEN",csrf)
                    .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(),HttpResponse.BodyHandlers.ofString());
        }
        private HttpResponse<String> sendPost(String path, Map<String, ?> payload, String csrf) throws Exception {
            var builder = request(path).header("Content-Type", "application/json");
            if (csrf != null) builder.header("X-XSRF-TOKEN", csrf);
            var body = payload == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload));
            return client.send(builder.POST(body).build(), HttpResponse.BodyHandlers.ofString());
        }
        private HttpRequest.Builder request(String path) {
            return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
        }
        @Override public void close() { client.close(); }
    }
}
