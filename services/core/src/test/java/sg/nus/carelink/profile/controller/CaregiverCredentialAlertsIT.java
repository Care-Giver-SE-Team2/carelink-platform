package sg.nus.carelink.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.client.MockMvcClientHttpRequestFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreApiClients;
import sg.nus.carelink.testsupport.SharedMySql;
import tools.jackson.databind.json.JsonMapper;

/**
 * A caregiver's credential reminders, as visit asks core for them for the caregiver's schedule:
 * core's alert policy on real MySQL, through core's internal API. The old certificate's reminder stays
 * until its renewal is approved and in effect; a renewal another caregiver holds does not count; a
 * broken chain is flagged for review; the window is 30 days, inclusive, on the Singapore date. visit's
 * own test checks that the schedule shows what core answers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(CaregiverCredentialAlertsIT.FixedTime.class)
class CaregiverCredentialAlertsIT {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, CaregiverCredentialAlertsIT.class, "+05:00");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTime {
		@Bean
		@Primary
		Clock credentialClock() {
			return Clock.fixed(Instant.parse("2026-09-24T16:30:00Z"), ZoneOffset.UTC);
		}
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private EntityManager entityManager;

	private CoreApi core;

	@BeforeEach
	void seedIsolatedAccounts() {
		core = CoreApiClients.create(RestClient.builder()
				.requestFactory(new MockMvcClientHttpRequestFactory(mvc))
				.baseUrl("http://core"));
		jdbc.update("insert into app_user(id,username,password_hash,display_name) values (7001,'credential-a','unused','A'),(7002,'credential-b','unused','B')");
		jdbc.update("insert into user_role(user_id,role) values (7001,'CAREGIVER'),(7002,'CAREGIVER')");
		jdbc.update("insert into caregiver(id,user_id,full_name,status) values (7101,7001,'A','AVAILABLE'),(7102,7002,'B','AVAILABLE')");
		jdbc.update("insert into credential_type(id,name) values (7201,'Demo renewal')");
		credential(7301, 7101, "PUBLISHED", null, TODAY.minusDays(1), null);
		credential(7302, 7102, "PUBLISHED", null, TODAY, null);
	}

	private void credential(long id, long owner, String state, LocalDate from, LocalDate expiry, Long renews) {
		jdbc.update("insert into credential(id,caregiver_id,credential_type_id,certificate_no,valid_from,expiry_date,status,renews_credential_id) values (?,?,7201,?,?,?,?,?)",
				id, owner, "PRIVATE-CERT-" + id, from, expiry, state, renews);
	}

	@ParameterizedTest
	@CsvSource({ "SUBMITTED,PENDING_REVIEW", "REJECTED,REJECTED", "REVOKED,REVOKED" })
	void retainsOldReminderAndReportsRenewalWithoutWritingCredentials(String state, String renewal) {
		credential(7303, 7101, state, null, TODAY.plusDays(365), 7301L);
		var before = jdbc.queryForList("select * from credential order by id");

		CoreApi.CredentialAlerts alerts = core.credentialAlerts(7101L, TODAY);

		assertThat(alerts.context()).isEqualTo(new CoreApi.CredentialAlertContext(TODAY, 30, false));
		assertThat(alerts.items()).singleElement().satisfies(alert -> {
			assertThat(alert.id()).isEqualTo(7301L);
			assertThat(alert.daysUntilExpiry()).isEqualTo(-1);
			assertThat(alert.renewalState()).isEqualTo(renewal);
		});
		assertThat(jdbc.queryForList("select * from credential order by id")).isEqualTo(before);
	}

	@Test
	void futureRenewalBecomesEffectiveWithoutChangingPublicFamilyProjection() {
		credential(7303, 7101, "PUBLISHED", TODAY.plusDays(7), TODAY.plusDays(365), 7301L);
		assertThat(core.credentialAlerts(7101L, TODAY).items().getFirst()).satisfies(alert -> {
			assertThat(alert.renewalState()).isEqualTo("APPROVED_NOT_EFFECTIVE");
			assertThat(alert.renewalValidFrom()).isEqualTo(LocalDate.of(2026, 10, 2));
		});
		jdbc.update("update credential set valid_from=? where id=7303", TODAY);
		// JDBC bypasses JPA's first-level cache in this test-wide transaction.
		// Real HTTP requests have separate persistence contexts; emulate that boundary.
		entityManager.clear();
		var publicBefore = core.caregiverPublicCredentials(7101L);
		assertThat(core.credentialAlerts(7101L, TODAY).items()).isEmpty();
		assertThat(core.caregiverPublicCredentials(7101L)).isEqualTo(publicBefore).hasSize(2);
		assertThat(JsonMapper.builder().build().writeValueAsString(publicBefore))
				.doesNotContain("certificateNo", "renewsCredentialId", "renewalState", "PRIVATE-CERT");
	}

	@Test
	void foreignRenewalCannotClearAnotherCaregiversReminder() {
		credential(7303, 7102, "PUBLISHED", null, TODAY.plusDays(365), 7301L);
		assertThat(core.credentialAlerts(7101L, TODAY).items()).singleElement()
				.extracting(CoreApi.CredentialAlert::id).isEqualTo(7301L);
		CoreApi.CredentialAlerts other = core.credentialAlerts(7102L, TODAY);
		assertThat(other.context().reviewRequired()).isTrue();
		assertThat(other.items().getFirst().id()).isEqualTo(7302L);
	}

	@Test
	void cyclicRowsKeepWarningsAndExposeOnlyAGenericReviewFlag() {
		credential(7303, 7101, "PUBLISHED", null, TODAY, 7301L);
		jdbc.update("update credential set renews_credential_id=7303 where id=7301");
		CoreApi.CredentialAlerts alerts = core.credentialAlerts(7101L, TODAY);
		assertThat(alerts.context().reviewRequired()).isTrue();
		assertThat(alerts.items()).hasSize(2);
		assertThat(alerts.items().getFirst().renewalState()).isEqualTo("CHECK_REQUIRED");
	}

	@Test
	void inclusiveWindowAndPermanentCredentialsUseTheDateAskedFor() {
		credential(7303, 7101, "PUBLISHED", null, TODAY, null);
		credential(7304, 7101, "PUBLISHED", null, TODAY.plusDays(30), null);
		credential(7305, 7101, "PUBLISHED", null, TODAY.plusDays(31), null);
		credential(7306, 7101, "PUBLISHED", null, LocalDate.of(9999, 12, 31), null);
		var alerts = core.credentialAlerts(7101L, TODAY).items();
		assertThat(alerts).hasSize(3);
		assertThat(alerts.get(1).daysUntilExpiry()).isZero();
		assertThat(alerts.get(2).daysUntilExpiry()).isEqualTo(30);
	}

}
