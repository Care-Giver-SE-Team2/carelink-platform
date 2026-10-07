package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.model.IntakeSubmission;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;
import sg.nus.carelink.profile.domain.repository.IntakeApplicationRepository;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * Verifies family intake submission and persistence using an isolated MySQL database.
 *
 * @author Wang Zhili
 */
@SpringBootTest
class IntakeSubmissionIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, IntakeSubmissionIT.class, null);
	}

	@Autowired
	private IntakeSubmissionService service;
	@Autowired
	private IntakeApplicationRepository applications;
	@Autowired
	private FamilyMemberRepository families;
	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void prepareAccountsInTheIsolatedTestDatabase() {
		jdbc.update("DELETE FROM intake_application");
		jdbc.update("DELETE FROM family_member");
		jdbc.update("DELETE FROM user_role");
		jdbc.update("DELETE FROM app_user");
		jdbc.update("INSERT INTO app_user (id, username, password_hash, display_name) VALUES "
				+ "(7, 'family-a', '{noop}unused-test-password', 'Family A'), "
				+ "(9, 'family-b', '{noop}unused-test-password', 'Family B'), "
				+ "(12, 'no-profile', '{noop}unused-test-password', 'Missing profile')");
		jdbc.update("INSERT INTO user_role (user_id, role) VALUES (7, 'FAMILY'), (9, 'FAMILY'), (12, 'FAMILY')");
		jdbc.update("INSERT INTO family_member (id, user_id, full_name) VALUES "
				+ "(42, 7, 'Family A'), (7, 9, 'Family B')");
	}

	@Test
	void savesAndReloadsTheFamilySubmissionIncludingJsonAndDatabaseCreationTime() {
		var details = new IntakeSubmission("Tan Mei", 80, "12 Example Road", "123456",
				IntakeApplication.MobilityLevel.ASSISTIVE_CANE, "Hokkien",
				List.of("BATHING", "Reminder: \"water\""), "Needs assistance");

		IntakeApplication saved = service.submit("family-a", details);

		assertThat(saved.id()).isPositive();
		assertThat(saved.applicantFamilyMemberId()).isEqualTo(42L);
		assertThat(saved.createdAt()).isNotNull();
		assertThat(saved.careNeeds()).containsExactly("BATHING", "Reminder: \"water\"");
		assertThat(applications.findById(saved.id())).contains(saved);
	}

	@Test
	void persistsDefaultsWithoutRequiringAnElderOrBinding() {
		IntakeApplication saved = service.submit("family-a",
				new IntakeSubmission("Tan Mei", null, "12 Example Road", "123456", null, null, null, null));

		IntakeApplication reloaded = applications.findById(saved.id()).orElseThrow();
		assertThat(reloaded.status()).isEqualTo(IntakeApplication.Status.SUBMITTED);
		assertThat(reloaded.mobilityLevel()).isEqualTo(IntakeApplication.MobilityLevel.INDEPENDENT);
		assertThat(reloaded.careNeeds()).isEmpty();
		assertThat(reloaded.targetElderAge()).isNull();
		assertThat(reloaded.reviewedByUserId()).isNull();
		assertThat(reloaded.reviewRemarks()).isNull();
		assertThat(reloaded.reviewedAt()).isNull();
		assertThat(reloaded.elderId()).isNull();
	}

	@Test
	void rejectsAnAccountWithoutARecordedFamilyProfile() {
		var details = new IntakeSubmission("Tan Mei", null, "12 Example Road", "123456", null, null, null, null);

		assertThatThrownBy(() -> service.submit("no-profile", details)).isInstanceOf(AccessDeniedException.class);
		assertThat(families.findByUserId(12L)).isEmpty();
	}
}
