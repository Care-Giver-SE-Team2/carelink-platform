package sg.nus.carelink.visit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import sg.nus.carelink.profile.application.CaregiverDirectory;
import sg.nus.carelink.shared.audit.application.AccessAudit;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.visit.domain.repository.VisitScheduleQuery;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies durable FM02 read audits, safe failure responses and unchanged business data.
 *
 * @author Wang Zhili
 */
@SpringBootTest(properties = {
		"spring.datasource.hikari.maximum-pool-size=1",
		"spring.datasource.hikari.connection-timeout=1000"
})
@AutoConfigureMockMvc
@Import(FamilyAccessAuditIT.FixedTime.class)
class FamilyAccessAuditIT {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 0, 30);
	private static final String FAILURE_SECRET = "PRIVATE-FAILURE password=hidden SELECT medical_notes";
	private static final List<ReadCase> READS = List.of(
			new ReadCase("/api/elders", "ELDER", null, "FM02_LIST_ELDERS", "$[0].id", 101),
			new ReadCase("/api/visits", "VISIT", null, "FM02_LIST_VISITS", "$.items[0].id", 301),
			new ReadCase("/api/caregivers/201", "CAREGIVER", 201L, "FM02_READ_CAREGIVER", "$.id", 201),
			new ReadCase("/api/caregivers/201/credentials", "CAREGIVER_CREDENTIALS", 201L,
					"FM02_LIST_CREDENTIALS", "$[0].id", 901));

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyAccessAuditIT.class, "+05:00");
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@MockitoSpyBean
	private AccessAudit audit;
	@MockitoSpyBean
	private CaregiverDirectory caregivers;
	@MockitoSpyBean
	private VisitScheduleQuery visits;
	private final JsonMapper json = JsonMapper.builder().build();

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTime {
		@Bean
		@Primary
		Clock familyAccessAuditClock() {
			return Clock.fixed(Instant.parse("2026-09-27T16:30:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void prepareIsolatedFamilyAccess() {
		jdbc.update("DELETE FROM audit_log");
		jdbc.update("DELETE FROM credential");
		jdbc.update("DELETE FROM credential_type");
		jdbc.update("DELETE FROM visit");
		jdbc.update("DELETE FROM elder_family_binding");
		jdbc.update("DELETE FROM elder");
		jdbc.update("DELETE FROM family_member");
		jdbc.update("DELETE FROM caregiver");
		jdbc.update("DELETE FROM user_role");
		jdbc.update("DELETE FROM app_user");
		jdbc.update("""
				INSERT INTO app_user (id, username, password_hash, display_name) VALUES
				(7, 'family-a', '{noop}test-password', 'Family A'),
				(9, 'family-b', '{noop}test-password', 'Family B'),
				(10, 'manager', '{noop}test-password', 'Manager'),
				(12, 'no-profile', '{noop}test-password', 'Missing profile'),
				(13, 'no-binding', '{noop}test-password', 'No binding')
				""");
		jdbc.update("""
				INSERT INTO user_role (user_id, role) VALUES
				(7, 'FAMILY'), (9, 'FAMILY'), (10, 'MANAGER'), (12, 'FAMILY'), (13, 'FAMILY')
				""");
		jdbc.update("""
				INSERT INTO family_member (id, user_id, full_name) VALUES
				(42, 7, 'Family A'), (7, 9, 'Family B'), (55, 13, 'No binding')
				""");
		jdbc.update("""
				INSERT INTO elder (id, full_name, address, medical_notes) VALUES
				(101, 'Confidential elder', 'PRIVATE-ADDRESS', 'PRIVATE-MEDICAL'),
				(110, 'Other elder', 'OTHER-ADDRESS', 'OTHER-MEDICAL')
				""");
		jdbc.update("""
				INSERT INTO caregiver (id, user_id, full_name, phone, status) VALUES
				(201, 1201, 'Confidential caregiver', 'PRIVATE-PHONE', 'AVAILABLE'),
				(204, 1204, 'Other caregiver', 'OTHER-PHONE', 'AVAILABLE')
				""");
		jdbc.update("""
				INSERT INTO elder_family_binding
				(id, elder_id, family_member_id, relationship, access_scope, status, confirmed_at)
				VALUES (501, 101, 42, 'DAUGHTER', 'FULL', 'ACTIVE', '2026-09-01 10:00:00'),
				(502, 110, 7, 'SON', 'READ_ONLY', 'ACTIVE', '2026-09-01 10:00:00')
				""");
		jdbc.update("""
				INSERT INTO visit (id, elder_id, caregiver_id, service_type, scheduled_start, status)
				VALUES (301, 101, 201, 'BATHING', '2026-09-28 10:00:00', 'SCHEDULED'),
				(304, 110, 204, 'BATHING', '2026-09-28 10:00:00', 'SCHEDULED')
				""");
		jdbc.update("INSERT INTO credential_type (id, name) VALUES (701, 'First Aid')");
		jdbc.update("""
				INSERT INTO credential (id, caregiver_id, credential_type_id, reviewed_by_user_id,
				certificate_no, issuing_body, valid_from, expiry_date, status, updated_at)
				VALUES (901, 201, 701, 10, 'PRIVATE-CERTIFICATE', 'Training provider',
				'2026-01-01', '2026-09-27', 'PUBLISHED', '2026-09-01 10:00:00'),
				(902, 201, 701, 10, 'PRIVATE-PENDING-CERT', 'Training provider',
				'2026-01-01', '2027-09-27', 'SUBMITTED', '2026-09-01 10:00:00')
				""");
	}

	@Test
	void eachFamilyReadAppendsOneAuditWithAccountIdentityAndSingaporeTimeWithoutChangingBusinessData()
			throws Exception {
		var session = loginAs("family-a");
		var before = businessSnapshot();
		assertThat(auditRows()).isEmpty();
		for (int index = 0; index < READS.size(); index++) {
			ReadCase read = READS.get(index);
			var preceding = auditRows();
			mvc.perform(get(read.path()).session(session))
					.andExpect(status().isOk())
					.andExpect(jsonPath(read.responsePath()).value(read.responseId()));
			var rows = auditRows();
			assertThat(rows).hasSize(index + 1);
			assertThat(rows.subList(0, index)).isEqualTo(preceding);
			assertAudit(rows.get(index), read, 7L, "OK");
		}
		assertThat(businessSnapshot()).isEqualTo(before);
	}

	@Test
	void businessQueryKeepsAnActiveReadOnlyTransactionBeforeTheAuditWrite() throws Exception {
		var active = new AtomicBoolean();
		var readOnly = new AtomicBoolean();
		doAnswer(invocation -> {
			active.set(TransactionSynchronizationManager.isActualTransactionActive());
			readOnly.set(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
			return invocation.callRealMethod();
		}).when(visits).hasAssignedVisit(anySet(), eq(201L));
		mvc.perform(get("/api/caregivers/201").session(loginAs("family-a")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(201));
		assertThat(active).isTrue();
		assertThat(readOnly).isTrue();
		assertThat(auditRows()).hasSize(1);
		assertAudit(auditRows().getFirst(), READS.get(2), 7L, "OK");
	}

	@ParameterizedTest
	@ValueSource(strings = { "elders", "visits", "credentials" })
	void emptySuccessfulListsStillCreateOkAudits(String operation) throws Exception {
		ReadCase read = switch (operation) {
			case "elders" -> READS.get(0);
			case "visits" -> READS.get(1);
			case "credentials" -> READS.get(3);
			default -> throw new IllegalArgumentException("Unknown read: " + operation);
		};
		boolean credentials = operation.equals("credentials");
		if (credentials) {
			jdbc.update("DELETE FROM credential WHERE id = 901");
		}
		mvc.perform(get(read.path()).session(loginAs(credentials ? "family-a" : "no-binding")))
				.andExpect(status().isOk())
				.andExpect(jsonPath(operation.equals("visits") ? "$.items" : "$").isEmpty());
		assertThat(auditRows()).hasSize(1);
		assertAudit(auditRows().getFirst(), read, credentials ? 7L : 13L, "OK");
	}

	@Test
	void visitAuditRecordsBoundedTypedFiltersAndExcludesRawQueriesAndPrivateData() throws Exception {
		mvc.perform(get("/api/visits").session(loginAs("family-a"))
				.param("elderId", "101").param("caregiverId", "201")
				.param("dateFrom", "2026-09-28").param("dateTo", "2026-09-29")
				.param("status", "SCHEDULED").param("page", "0").param("size", "5")
				.param("ignoredRawQuery", "PRIVATE-RAW-QUERY".repeat(100)))
				.andExpect(status().isOk());
		assertThat(auditRows()).hasSize(1);
		var row = auditRows().getFirst();
		assertAudit(row, READS.get(1), 7L, "OK");
		assertThat(row.detail()).contains("101", "201", "2026-09-28", "2026-09-29", "SCHEDULED", "page=0", "size=5")
				.doesNotContain("PRIVATE-RAW-QUERY");
	}

	@ParameterizedTest
	@MethodSource("relationshipReads")
	void revokedBindingCreatesADurableDeniedAuditWhenTheReadTransactionRollsBack(ReadCase read) throws Exception {
		var session = loginAs("family-a");
		jdbc.update("UPDATE elder_family_binding SET status = 'REVOKED' WHERE id = 501");
		var request = get(read.path()).session(session);
		if (read.resourceType().equals("VISIT")) {
			request.param("elderId", "101");
		}
		mvc.perform(request).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
		assertThat(auditRows()).hasSize(1);
		assertAudit(auditRows().getFirst(), read, 7L, "DENIED");
	}

	@ParameterizedTest
	@MethodSource("readCases")
	void missingFamilyProfileStillAuditsTheLoggedInAccountIdentifier(ReadCase read) throws Exception {
		mvc.perform(get(read.path()).session(loginAs("no-profile")))
				.andExpect(status().isForbidden());
		assertThat(auditRows()).hasSize(1);
		assertAudit(auditRows().getFirst(), read, 12L, "DENIED");
	}

	@ParameterizedTest
	@ValueSource(strings = { "profile", "credentials" })
	void anAuthorizedMissingCaregiverCreatesFailedAuditAndPreservesNotFound(String operation) throws Exception {
		ReadCase read;
		if (operation.equals("profile")) {
			read = READS.get(2);
			doReturn(Optional.empty()).when(caregivers).findPublicProfile(201L);
		} else {
			read = READS.get(3);
			doThrow(new ResourceNotFound("Caregiver", 201L)).when(caregivers).listPublicCredentials(201L);
		}
		mvc.perform(get(read.path()).session(loginAs("family-a")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
		assertThat(auditRows()).hasSize(1);
		assertAudit(auditRows().getFirst(), read, 7L, "FAILED");
	}

	@Test
	void unexpectedReadFailureIsAuditedWithoutPersistingItsExceptionMessage() throws Exception {
		doThrow(new DataAccessResourceFailureException(FAILURE_SECRET)).when(caregivers).findPublicProfile(201L);
		var response = mvc.perform(get(READS.get(2).path()).session(loginAs("family-a")))
				.andExpect(status().isInternalServerError()).andReturn().getResponse();
		assertThat(response.getContentAsString()).doesNotContain(FAILURE_SECRET, "Confidential caregiver");
		assertThat(auditRows()).hasSize(1);
		assertAudit(auditRows().getFirst(), READS.get(2), 7L, "FAILED");
		assertThat(auditRows().getFirst().detail()).doesNotContain(FAILURE_SECRET, "SELECT", "password");
	}

	@Test
	void anonymousAndMalformedRequestsDoNotProduceApplicationReadAudits() throws Exception {
		for (ReadCase read : READS) {
			mvc.perform(get(read.path())).andExpect(status().isUnauthorized());
		}
		var session = loginAs("family-a");
		mvc.perform(get("/api/visits").session(session).param("page", "not-a-number"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/caregivers/not-a-number").session(session)).andExpect(status().isBadRequest());
		mvc.perform(get("/api/caregivers/not-a-number/credentials").session(session)).andExpect(status().isBadRequest());
		assertThat(auditRows()).isEmpty();
	}

	@Test
	void managerKeepsTheExistingElderListAndDoesNotCreateFamilyAudits() throws Exception {
		var session = loginAs("manager");
		mvc.perform(get("/api/elders").session(session))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
		for (ReadCase read : READS.subList(1, READS.size())) {
			mvc.perform(get(read.path()).session(session)).andExpect(status().isForbidden());
		}
		assertThat(auditRows()).isEmpty();
	}

	@ParameterizedTest
	@MethodSource("readCases")
	void unavailableAuditStorageReturnsSafe503InsteadOfProtectedData(ReadCase read) throws Exception {
		doThrow(new DataAccessResourceFailureException(FAILURE_SECRET)).when(audit).append(any(AccessAuditEntry.class));
		var before = businessSnapshot();
		assertSafeAuditFailure(read, loginAs("family-a"));
		assertThat(auditRows()).isEmpty();
		assertThat(businessSnapshot()).isEqualTo(before);
	}

	@Test
	void unavailableAuditStorageAlsoFailsClosedForADeniedRead() throws Exception {
		doThrow(new DataAccessResourceFailureException(FAILURE_SECRET)).when(audit).append(any(AccessAuditEntry.class));
		assertSafeAuditFailure(READS.get(2), loginAs("no-profile"));
		assertThat(auditRows()).isEmpty();
	}

	private void assertSafeAuditFailure(ReadCase read, MockHttpSession session) throws Exception {
		var response = mvc.perform(get(read.path()).session(session))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(503))
				.andExpect(jsonPath("$.items").doesNotExist())
				.andExpect(jsonPath("$.fullName").doesNotExist())
				.andReturn().getResponse();
		assertThat(response.getContentAsString()).doesNotContain(FAILURE_SECRET, "Confidential elder",
				"Confidential caregiver", "PRIVATE-", "Training provider", "First Aid");
	}

	private void assertAudit(AuditRow row, ReadCase read, long actorUserId, String result) {
		assertThat(row.actorUserId()).isEqualTo(actorUserId);
		assertThat(row.action()).isEqualTo("READ");
		assertThat(row.resourceType()).isEqualTo(read.resourceType());
		assertThat(row.resourceId()).isEqualTo(read.resourceId());
		assertThat(row.result()).isEqualTo(result);
		assertThat(row.occurredAt()).isEqualTo(NOW);
		assertThat(row.detail()).contains(read.operation()).hasSizeLessThanOrEqualTo(500)
				.doesNotContain("PRIVATE-", "Confidential", "test-password", "{noop}", "Training provider", "First Aid");
	}

	private List<AuditRow> auditRows() {
		return jdbc.query("SELECT * FROM audit_log ORDER BY id", (row, index) -> new AuditRow(
				row.getLong("id"), row.getLong("actor_user_id"), row.getString("action"), row.getString("resource_type"),
				(Long) row.getObject("resource_id"), row.getString("result"), row.getString("detail"),
				row.getObject("occurred_at", LocalDateTime.class)));
	}

	private Map<String, List<Map<String, Object>>> businessSnapshot() {
		var snapshot = new LinkedHashMap<String, List<Map<String, Object>>>();
		for (String table : List.of("elder", "family_member", "elder_family_binding", "caregiver", "visit",
				"credential_type", "credential")) {
			snapshot.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
		}
		return snapshot;
	}

	private MockHttpSession loginAs(String username) throws Exception {
		Cookie token = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
				.andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(token).isNotNull();
		var result = mvc.perform(post("/api/auth/login").cookie(token).header("X-XSRF-TOKEN", token.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("username", username, "password", "test-password"))))
				.andExpect(status().isOk()).andReturn();
		var session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session).isNotNull();
		return session;
	}

	private static Stream<ReadCase> readCases() {
		return READS.stream();
	}

	private static Stream<ReadCase> relationshipReads() {
		return READS.subList(1, READS.size()).stream();
	}

	private record ReadCase(String path, String resourceType, Long resourceId, String operation,
			String responsePath, int responseId) {
	}

	private record AuditRow(long id, long actorUserId, String action, String resourceType, Long resourceId,
			String result, String detail, LocalDateTime occurredAt) {
	}
}
