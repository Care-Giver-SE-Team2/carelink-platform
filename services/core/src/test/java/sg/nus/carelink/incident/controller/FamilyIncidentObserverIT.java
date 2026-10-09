package sg.nus.carelink.incident.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.incident.application.IncidentFamilyEvents;
import sg.nus.carelink.incident.domain.repository.FamilyAlertDeliveryStore;
import sg.nus.carelink.testsupport.SharedMySql;

/** Real commit/rollback, per-recipient failures, replay and window persistence. @author Wang Zhili */
@SpringBootTest(properties = {"carelink.report.schedule-cron=-", "carelink.escalation.scan-initial-delay=PT1H"})
@AutoConfigureMockMvc
@Import(FamilyIncidentReceiptIT.TimeConfiguration.class)
class FamilyIncidentObserverIT {
    private static final Instant START = Instant.parse("2026-10-07T08:00:00Z");
    private static final OffsetDateTime OCCURRED = OffsetDateTime.parse("2026-10-07T07:00:00.123456789Z");
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FamilyIncidentReceiptIT.TestClock clock;
    @Autowired private IncidentFamilyEvents events;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private FamilyAlertDeliveryStore store;
    private final JsonMapper json = JsonMapper.builder().build();
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SharedMySql.register(registry, FamilyIncidentObserverIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
    }
	@BeforeEach
	void prepare() {
		clock.now = START;
		for (String table : List.of("family_alert_window", "family_alert_delivery", "family_alert_event", "audit_log", "notification_subscription", "notification", "incident_acknowledgement", "incident_log",
				"incident", "elder_family_binding", "elder", "family_member", "user_role", "app_user")) {
			jdbc.update("DELETE FROM " + table);
		}
		jdbc.update("""
				INSERT INTO app_user (id, username, password_hash, display_name) VALUES
				(7, 'family-a', '{noop}test-password', 'Family A'), (9, 'family-b', '{noop}test-password', 'Family B'),
				(10, 'manager', '{noop}test-password', 'Manager'), (12, 'no-profile', '{noop}test-password', 'No profile'),
				(13, 'caregiver', '{noop}test-password', 'Caregiver'), (14, 'elder', '{noop}test-password', 'Elder')
				""");
		jdbc.update("INSERT INTO user_role (user_id, role) VALUES (7, 'FAMILY'), (9, 'FAMILY'), (10, 'MANAGER'), (12, 'FAMILY'), (13, 'CAREGIVER'), (14, 'ELDER')");
		jdbc.update("INSERT INTO family_member (id, user_id, full_name) VALUES (42, 7, 'Family A'), (7, 9, 'Family B')");
		jdbc.update("INSERT INTO elder (id, full_name) VALUES (101, 'Elder A'), (102, 'Elder B'), (103, 'Elder C')");
		jdbc.update("""
				INSERT INTO elder_family_binding (elder_id, family_member_id, relationship, access_scope, status) VALUES
				(101, 42, 'DAUGHTER', 'FULL', 'ACTIVE'), (102, 42, 'SON', 'READ_ONLY', 'ACTIVE'), (101, 7, 'SON', 'FULL', 'ACTIVE')
				""");
		for (long id : List.of(601L, 602L, 603L)) {
			jdbc.update("""
					INSERT INTO incident (id, elder_id, responder_user_id, source, category, severity, status, description, reported_at, respond_by)
					VALUES (?, ?, 10, 'CAREGIVER', 'FALL', 'HIGH', ?, 'Care details', ?, ?)
					""", id, id - 500, id == 602 ? "RESOLVED" : "OPEN",
					Timestamp.valueOf(LocalDateTime.of(2026, 10, 7, 15, 0)), Timestamp.valueOf(LocalDateTime.of(2026, 10, 7, 15, 5)));
		}
		jdbc.update("""
				INSERT INTO notification (id, recipient_user_id, event_type, title, resource_type, resource_id, status)
				VALUES (801, 7, 'INCIDENT_RAISED', 'Care alert', 'INCIDENT', 601, 'SENT')
				""");
		jdbc.update("DELETE FROM notification");
	}


	@Test void onlyCommittedEventsCreateSafePendingMessagesAndPersonalWindows() throws Exception {
		UUID id = UUID.randomUUID();
		transaction().executeWithoutResult(status -> {
			jdbc.update("UPDATE incident SET description = 'Committed private detail' WHERE id = 601");
			events.raised(id, 601L, 101L, OCCURRED);
			assertThat(count("notification")).isZero();
			assertThat(count("family_alert_event")).isZero();
		});
		assertThat(count("notification")).isEqualTo(2);
		assertThat(count("family_alert_window")).isEqualTo(2);
		assertThat(count("incident_acknowledgement")).isZero();
		assertThat(jdbc.queryForObject("SELECT state FROM family_alert_event", String.class)).isEqualTo("PROCESSED");
		assertThat(jdbc.queryForList("SELECT * FROM notification")).allSatisfy(row -> {
			assertThat(row.get("recipient_user_id")).isIn(7L, 9L);
			assertThat(row.get("resource_type")).isEqualTo("INCIDENT");
			assertThat(row.get("status")).isEqualTo("PENDING");
			assertThat(row.get("channel")).isEqualTo("IN_APP");
			assertThat(row.get("title").toString()).contains("HIGH");
			assertThat(row.get("body").toString()).doesNotContain("private", "Committed", "description");
			assertThat(row.get("read_at")).isNull();
		});
		assertThat(read(loginAs("family-a"), 601).path("acknowledgeBy").asString()).isEqualTo("2026-10-07T18:00:00+08:00");
	}

	@Test void rollbackAndRejectedPublicationDoNotReachObservers() {
		transaction().executeWithoutResult(status -> { events.raised(UUID.randomUUID(), 601L, 101L, OCCURRED); status.setRollbackOnly(); });
		assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
			events.raised(UUID.randomUUID(), 601L, 101L, OCCURRED); throw new IllegalStateException("Source save failed");
		})).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> events.raised(UUID.randomUUID(), 601L, 101L, OCCURRED)).isInstanceOf(IllegalStateException.class);
		var readOnly = transaction(); readOnly.setReadOnly(true);
		assertThatThrownBy(() -> readOnly.executeWithoutResult(status -> events.raised(UUID.randomUUID(), 601L, 101L, OCCURRED)))
				.isInstanceOf(IllegalStateException.class);
		assertThat(count("notification")).isZero();
		assertThat(count("family_alert_event")).isZero();
	}

	@Test void concurrentReplayCreatesOneNoticePerFamilyAndPreservesIdentity() throws Exception {
		UUID id = UUID.randomUUID();
		var start = new CountDownLatch(1);
		try (var pool = Executors.newFixedThreadPool(4)) {
			var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
			for (int index = 0; index < 4; index++) {
				futures.add(pool.submit(() -> { assertThat(start.await(10, TimeUnit.SECONDS)).isTrue(); publish(id); return null; }));
			}
			start.countDown();
			for (var future : futures) { future.get(20, TimeUnit.SECONDS); }
		}
		assertThat(count("notification")).isEqualTo(2);
		assertThat(count("family_alert_delivery")).isEqualTo(2);
		assertThat(count("family_alert_event")).isEqualTo(1);
		var messages = jdbc.queryForList("SELECT * FROM notification ORDER BY id");
		clock.now = START.plusSeconds(3600);
		publish(id);
		assertThat(jdbc.queryForList("SELECT * FROM notification ORDER BY id")).isEqualTo(messages);
		// A failed attempt's follow-up may race a successful retry; it cannot undo CREATED.
		transaction().executeWithoutResult(status -> store.failed(id, 42L, LocalDateTime.of(2026, 10, 7, 17, 0)));
		assertThat(jdbc.queryForObject("SELECT status FROM family_alert_delivery WHERE family_member_id=42", String.class)).isEqualTo("CREATED");
	}

	@Test void anotherEventAndInboxDeliveryDoNotRestartTheFirstWindow() throws Exception {
		publish(UUID.randomUUID());
		var windows = jdbc.queryForList("SELECT * FROM family_alert_window ORDER BY family_member_id");
		clock.now = START.plusSeconds(3600);
		transaction().executeWithoutResult(status -> {
			jdbc.update("UPDATE incident SET status = 'UNRESOLVED_ESCALATED' WHERE id = 601");
			events.unresolved(UUID.randomUUID(), 601L, 101L, OCCURRED.plusMinutes(30));
		});
		assertThat(count("notification")).isEqualTo(4);
		assertThat(jdbc.queryForList("SELECT * FROM family_alert_window ORDER BY family_member_id")).isEqualTo(windows);
		Browser family = loginAs("family-a");
		mvc.perform(get("/api/notifications/me").session(family.session())).andExpect(status().isOk());
		assertThat(read(family, 601).path("acknowledgeBy").asString()).isEqualTo("2026-10-07T18:00:00+08:00");
		clock.now = START.plusSeconds(3 * 3600);
		command(family, 601, "acknowledge", null);
		assertThat(jdbc.queryForList("SELECT * FROM family_alert_window ORDER BY family_member_id")).isEqualTo(windows);
	}

	@Test void earlierAcknowledgementSurvivesLaterMessageAndNoViewIsInvented() throws Exception {
		Browser family = loginAs("family-a");
		JsonNode acknowledgement = command(family, 601, "acknowledge", "{\"responseNote\":\"Already known\"}");
		clock.now = START.plusSeconds(3600);
		publish(UUID.randomUUID());
		JsonNode detail = read(family, 601);
		assertThat(detail.path("acknowledgement")).isEqualTo(acknowledgement);
		assertThat(detail.path("acknowledgeBy").asString()).isEqualTo("2026-10-07T19:00:00+08:00");
		assertThat(detail.path("acknowledgement").path("viewedAt").isNull()).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"REVOKED", "REJECTED", "PENDING_CONFIRMATION", "EXPIRED", "DISABLED", "NO_ROLE", "NO_ACCOUNT"})
	void excludedFamiliesHaveReasonsAndNoNoticeOrWindow(String change) {
		switch (change) {
			case "EXPIRED" -> jdbc.update("UPDATE elder_family_binding SET expires_at = ? WHERE family_member_id=7", Timestamp.valueOf(LocalDateTime.of(2026,10,7,16,0)));
			case "DISABLED" -> jdbc.update("UPDATE app_user SET enabled=false WHERE id=9");
			case "NO_ROLE" -> jdbc.update("DELETE FROM user_role WHERE user_id=9");
			case "NO_ACCOUNT" -> jdbc.update("UPDATE family_member SET user_id=NULL WHERE id=7");
			default -> jdbc.update("UPDATE elder_family_binding SET status=? WHERE family_member_id=7", change);
		}
		publish(UUID.randomUUID());
		assertThat(count("notification")).isEqualTo(1);
		assertThat(count("family_alert_window")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT status FROM family_alert_delivery WHERE family_member_id=7", String.class)).isEqualTo("SKIPPED");
		assertThat(jdbc.queryForObject("SELECT reason FROM family_alert_delivery WHERE family_member_id=7", String.class)).isNotBlank();
		assertThat(jdbc.queryForObject("SELECT recipient_user_id FROM notification", Long.class)).isEqualTo(7L);
	}

	@Test void readOnlyBindingReceivesUrgentAlertsRegardlessOfOptionalSubscription() throws Exception {
		jdbc.update("UPDATE elder_family_binding SET access_scope='READ_ONLY' WHERE elder_id=101");
		jdbc.update("INSERT INTO notification_subscription (user_id, elder_id, event_type, channel, enabled) VALUES (7, 101, 'INCIDENT_RAISED', 'IN_APP', false)");
		publish(UUID.randomUUID());
		assertThat(count("notification")).isEqualTo(2);
		assertThat(read(loginAs("family-a"), 601).path("acknowledgeBy").asString()).isEqualTo("2026-10-07T18:00:00+08:00");
	}

	@Test void noBindingsAndAllExcludedAudiencesAreRecorded() {
		transaction().executeWithoutResult(status -> events.raised(UUID.randomUUID(), 603L, 103L, OCCURRED));
		assertThat(jdbc.queryForObject("SELECT state FROM family_alert_event", String.class)).isEqualTo("NO_RECIPIENTS");
		jdbc.update("UPDATE elder_family_binding SET status='REVOKED' WHERE elder_id=101");
		publish(UUID.randomUUID());
		assertThat(jdbc.queryForList("SELECT state FROM family_alert_event")).extracting(row -> row.get("state")).containsOnly("NO_RECIPIENTS");
		assertThat(count("notification")).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"notification", "family_alert_window", "family_alert_delivery"})
	void oneRecipientFailureRollsBackItsWholeDeliveryAndCanRetryWithoutDuplicatingOthers(String table) {
		String condition = table.equals("notification") ? "NEW.recipient_user_id = 9" : "NEW.family_member_id = 7";
		String operation = table.equals("family_alert_delivery") ? "UPDATE" : "INSERT";
		if (table.equals("family_alert_delivery")) { condition += " AND NEW.status = 'CREATED'"; }
		jdbc.execute("CREATE TRIGGER fm05_reject BEFORE " + operation + " ON " + table + " FOR EACH ROW BEGIN IF " + condition
				+ " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Forced family delivery failure'; END IF; END");
		UUID id = UUID.randomUUID();
		try {
			transaction().executeWithoutResult(status -> {
				jdbc.update("UPDATE incident SET description='Source committed' WHERE id=601");
				events.raised(id, 601L, 101L, OCCURRED);
			});
			assertThat(jdbc.queryForObject("SELECT description FROM incident WHERE id=601", String.class)).isEqualTo("Source committed");
			assertThat(count("notification")).isEqualTo(1);
			assertThat(count("family_alert_window")).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT status FROM family_alert_delivery WHERE family_member_id=7", String.class)).isEqualTo("FAILED");
			assertThat(jdbc.queryForObject("SELECT state FROM family_alert_event", String.class)).isEqualTo("FAILED");
		} finally { jdbc.execute("DROP TRIGGER fm05_reject"); }
		clock.now = START.plusSeconds(3600);
		publish(id);
		assertThat(count("notification")).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT state FROM family_alert_event", String.class)).isEqualTo("PROCESSED");
		var a = transaction().execute(status -> store.acknowledgeBy(601L, 42L));
		var b = transaction().execute(status -> store.acknowledgeBy(601L, 7L));
		assertThat(a).contains(LocalDateTime.of(2026,10,7,18,0));
		assertThat(b).contains(LocalDateTime.of(2026,10,7,19,0));
	}

	@Test void unknownMismatchedAndInvalidUnresolvedEventsHaveFailedOutcomes() {
		transaction().executeWithoutResult(status -> events.raised(UUID.randomUUID(), 999L, 101L, OCCURRED));
		transaction().executeWithoutResult(status -> events.raised(UUID.randomUUID(), 601L, 103L, OCCURRED));
		transaction().executeWithoutResult(status -> events.unresolved(UUID.randomUUID(), 601L, 101L, OCCURRED));
		assertThat(jdbc.queryForList("SELECT state FROM family_alert_event")).extracting(row -> row.get("state")).containsOnly("FAILED");
		assertThat(count("notification")).isZero();
	}

	@Test void reusedEventIdCannotChangeTheOriginalFacts() {
		UUID id = UUID.randomUUID(); publish(id);
		transaction().executeWithoutResult(status -> events.raised(id, 601L, 103L, OCCURRED));
		assertThat(count("notification")).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT elder_id FROM family_alert_event", Long.class)).isEqualTo(101L);
		assertThat(jdbc.queryForObject("SELECT state FROM family_alert_event", String.class)).isEqualTo("FAILED");
	}

	@Test void unavailableOutcomeStorageDoesNotUndoTheSourceCommit() {
		jdbc.execute("RENAME TABLE family_alert_event TO fm05_unavailable_event");
		try {
			transaction().executeWithoutResult(status -> {
				jdbc.update("UPDATE incident SET description='Still committed' WHERE id=601");
				events.raised(UUID.randomUUID(), 601L, 101L, OCCURRED);
			});
			assertThat(jdbc.queryForObject("SELECT description FROM incident WHERE id=601", String.class)).isEqualTo("Still committed");
			assertThat(count("notification")).isZero();
		} finally { jdbc.execute("RENAME TABLE fm05_unavailable_event TO family_alert_event"); }
	}

	@Test void unavailableRecipientFailureRecordingStillAllowsOtherFamilies() {
		jdbc.execute("CREATE TRIGGER fm05_message_fail BEFORE INSERT ON notification FOR EACH ROW BEGIN IF NEW.recipient_user_id=9 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Forced message failure'; END IF; END");
		jdbc.execute("CREATE TRIGGER fm05_outcome_fail BEFORE INSERT ON family_alert_delivery FOR EACH ROW BEGIN IF NEW.status='FAILED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Forced outcome failure'; END IF; END");
		try {
			publish(UUID.randomUUID());
			assertThat(count("notification")).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT recipient_user_id FROM notification", Long.class)).isEqualTo(7L);
			assertThat(jdbc.queryForObject("SELECT state FROM family_alert_event", String.class)).isEqualTo("FAILED");
		} finally {
			jdbc.execute("DROP TRIGGER fm05_message_fail");
			jdbc.execute("DROP TRIGGER fm05_outcome_fail");
		}
	}

	@Test void legacyMessagesAreUntouchedAndDoNotInventPersonalWindows() throws Exception {
		jdbc.update("INSERT INTO notification (id, recipient_user_id, event_type, title, resource_type, resource_id, status) VALUES (801,7,'INCIDENT_RAISED','Legacy notice','INCIDENT',601,'SENT')");
		var original = jdbc.queryForList("SELECT * FROM notification WHERE id=801");
		assertThat(read(loginAs("family-a"), 601).path("acknowledgeBy").isNull()).isTrue();
		publish(UUID.randomUUID());
		assertThat(jdbc.queryForList("SELECT * FROM notification WHERE id=801")).isEqualTo(original);
	}

	private record Browser(MockHttpSession session, Cookie csrf) { }

	private Browser loginAs(String username) throws Exception {
		var token = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		var result = mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username", username, "password", "test-password"))))
				.andExpect(status().isOk()).andReturn();
		return new Browser((MockHttpSession) result.getRequest().getSession(false), token);
	}

	private MockHttpServletRequestBuilder request(Browser browser, long id, String action, String body) {
		var request = post("/api/incidents/{id}/" + action, id).session(browser.session()).cookie(browser.csrf())
				.header("X-XSRF-TOKEN", browser.csrf().getValue());
		return body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private JsonNode command(Browser browser, long id, String action, String body) throws Exception {
		return json.readTree(mvc.perform(request(browser, id, action, body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private JsonNode read(Browser browser, long id) throws Exception {
		return json.readTree(mvc.perform(get("/api/family/incidents/{id}", id).session(browser.session()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

    private TransactionTemplate transaction() { return new TransactionTemplate(transactions); }
    private void publish(UUID id) { transaction().executeWithoutResult(status -> events.raised(id, 601L, 101L, OCCURRED)); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
}
