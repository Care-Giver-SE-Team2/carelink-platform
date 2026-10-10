package sg.nus.carelink.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreApiClients;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * The calls report and the inbox make of core, in the running application over real HTTP against
 * MySQL: an account as it stands, the enabled holders of a role, an elder as a report describes
 * them, and whether a caregiver is on leave that day.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"carelink.report.schedule-cron=-",
		"carelink.escalation.scan-initial-delay=PT1H"})
class CoreApiReadsIT {

	private static final AtomicLong ACCOUNTS = new AtomicLong(970_000);

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	private CoreApi core;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, CoreApiReadsIT.class, null, "connectionTimeZone=Asia/Singapore");
	}

	@BeforeEach
	void client() {
		core = CoreApiClients.create(RestClient.builder().baseUrl("http://localhost:" + port));
	}

	@Test
	void anAccountAsItStandsAndTheEnabledHoldersOfARole() {
		long lee = account("reads-lee", true, "MANAGER", "FAMILY");
		long ong = account("reads-ong", false, "MANAGER");

		CoreApi.Account found = core.account("reads-lee");
		assertThat(found.userId()).isEqualTo(lee);
		assertThat(found.enabled()).isTrue();
		assertThat(found.roles()).containsExactlyInAnyOrder("MANAGER", "FAMILY");
		assertThat(core.account("reads-ong").enabled()).isFalse();
		assertThat(core.findAccount("reads-nobody")).isEmpty();
		assertThat(core.enabledAccountsWithRole("MANAGER").userIds()).contains(lee).doesNotContain(ong);
	}

	@Test
	void anElderAsAReportDescribesThemWithTheLatestPublishedPlan() {
		jdbc.update("INSERT INTO elder (full_name, gender, date_of_birth, mobility_level, lives_alone, medical_notes)"
				+ " VALUES ('Tan Ah Kow', 'MALE', '1941-05-02', 'ASSISTIVE_CANE', true, 'Diabetic')");
		long elder = jdbc.queryForObject("SELECT last_insert_id()", Long.class);
		long siti = caregiver("Siti Rahman");
		jdbc.update("INSERT INTO elder_primary_caregiver (elder_id, caregiver_id) VALUES (?, ?)", elder, siti);
		jdbc.update("INSERT INTO care_plan (elder_id, version, status, total_hours) VALUES (?, 1, 'SUPERSEDED', 4.00),"
				+ " (?, 2, 'PUBLISHED', 6.50), (?, 3, 'DRAFT', 8.00)", elder, elder, elder);

		CoreApi.ElderReportProfile profile = core.elderReportProfile(elder);

		assertThat(profile.fullName()).isEqualTo("Tan Ah Kow");
		assertThat(profile.gender()).isEqualTo("MALE");
		assertThat(profile.dateOfBirth()).isEqualTo(LocalDate.of(1941, 5, 2));
		assertThat(profile.mobilityLevel()).isEqualTo("ASSISTIVE_CANE");
		assertThat(profile.livesAlone()).isTrue();
		assertThat(profile.primaryCaregiverId()).isEqualTo(siti);
		assertThat(profile.primaryCaregiverName()).isEqualTo("Siti Rahman");
		assertThat(profile.planVersion()).as("the latest published, not the draft").isEqualTo(2);
		assertThat(profile.planWeeklyHours()).isEqualByComparingTo("6.5");
	}

	@Test
	void aCaregiverIsOnLeaveOnTheDaysOfAnApprovedAbsenceOnly() {
		long siti = caregiver("Siti Rahman");
		jdbc.update("INSERT INTO absence_report (caregiver_id, start_date, end_date, status) VALUES"
				+ " (?, '2026-10-12', '2026-10-14', 'APPROVED'), (?, '2026-10-20', '2026-10-20', 'PENDING')", siti, siti);

		assertThat(core.onLeave(siti, LocalDate.of(2026, 10, 12)).onLeave()).isTrue();
		assertThat(core.onLeave(siti, LocalDate.of(2026, 10, 14)).onLeave()).isTrue();
		assertThat(core.onLeave(siti, LocalDate.of(2026, 10, 15)).onLeave()).isFalse();
		assertThat(core.onLeave(siti, LocalDate.of(2026, 10, 20)).onLeave()).as("not approved").isFalse();
	}

	private long account(String username, boolean enabled, String... roles) {
		jdbc.update("INSERT INTO app_user (username, password_hash, display_name, enabled) VALUES (?, '{noop}unused', ?, ?)",
				username, username, enabled);
		long id = jdbc.queryForObject("SELECT last_insert_id()", Long.class);
		for (String role : roles) {
			jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, ?)", id, role);
		}
		return id;
	}

	private long caregiver(String name) {
		jdbc.update("INSERT INTO caregiver (user_id, full_name, status) VALUES (?, ?, 'AVAILABLE')",
				ACCOUNTS.incrementAndGet(), name);
		return jdbc.queryForObject("SELECT last_insert_id()", Long.class);
	}

}
