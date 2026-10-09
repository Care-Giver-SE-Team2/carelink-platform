package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.domain.repository.ElderFamilyBindingRepository;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * Verifies family access against real account and binding rows in isolated MySQL.
 *
 * @author Wang Zhili
 */
@SpringBootTest
@Import(FamilyAccessQueryIT.FixedTime.class)
class FamilyAccessQueryIT {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 10, 0);

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, FamilyAccessQueryIT.class, "+05:00");
	}

	@Autowired
	private FamilyAccessQuery access;
	@Autowired
	private ElderFamilyBindingRepository bindings;
	@Autowired
	private JdbcTemplate jdbc;

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedTime {
		@Bean
		@Primary
		Clock familyAccessClock() {
			return Clock.fixed(Instant.parse("2026-09-23T02:00:00Z"), ZoneOffset.UTC);
		}
	}

	@BeforeEach
	void prepareIsolatedRelationships() {
		jdbc.update("DELETE FROM elder_family_binding");
		jdbc.update("DELETE FROM elder");
		jdbc.update("DELETE FROM family_member");
		jdbc.update("DELETE FROM user_role");
		jdbc.update("DELETE FROM app_user");
		jdbc.update("""
				INSERT INTO app_user (id, username, password_hash, display_name) VALUES
				(7, 'family-a', '{noop}unused-test-password', 'Family A'),
				(9, 'family-b', '{noop}unused-test-password', 'Family B'),
				(12, 'no-profile', '{noop}unused-test-password', 'Missing profile')
				""");
		jdbc.update("INSERT INTO user_role (user_id, role) VALUES (7, 'FAMILY'), (9, 'FAMILY'), (12, 'FAMILY')");
		jdbc.update("INSERT INTO family_member (id, user_id, full_name) VALUES (42, 7, 'Family A'), (7, 9, 'Family B')");
		for (long id : List.of(101L, 102L, 103L, 104L, 105L, 106L, 107L, 110L)) {
			jdbc.update("INSERT INTO elder (id, full_name) VALUES (?, ?)", id, "Test elder " + id);
		}
		seedBinding(101, 42, "FULL", "ACTIVE", null);
		seedBinding(102, 42, "READ_ONLY", "ACTIVE", NOW.plusSeconds(1));
		seedBinding(103, 42, "FULL", "ACTIVE", NOW);
		seedBinding(104, 42, "FULL", "ACTIVE", NOW.minusSeconds(1));
		seedBinding(105, 42, "FULL", "PENDING_CONFIRMATION", null);
		seedBinding(106, 42, "FULL", "REJECTED", null);
		seedBinding(107, 42, "FULL", "REVOKED", null);
		seedBinding(110, 7, "FULL", "ACTIVE", null);
	}

	@Test
	void queriesOnlyTheFamilyProfilesBindingsAndAppliesSingaporeExpiryBoundaries() {
		assertThat(bindings.findByFamilyMemberId(42L)).extracting(ElderFamilyBinding::elderId)
				.containsExactly(101L, 102L, 103L, 104L, 105L, 106L, 107L);
		assertThat(access.readableElderIds("family-a")).containsExactlyInAnyOrder(101L, 102L);
		assertThat(access.readableElderIds("family-b")).containsExactly(110L);
		access.requireReadableElder("family-a", 102L);
		for (long elderId : List.of(103L, 104L, 105L, 106L, 107L, 110L, 999L)) {
			assertThatThrownBy(() -> access.requireReadableElder("family-a", elderId))
					.isInstanceOf(AccessDeniedException.class);
		}
	}

	@Test
	void readsTheRevokedStateOnTheNextCall() {
		access.requireReadableElder("family-a", 101L);
		jdbc.update("UPDATE elder_family_binding SET status = 'REVOKED' WHERE elder_id = 101 AND family_member_id = 42");

		assertThat(access.readableElderIds("family-a")).containsExactly(102L);
		assertThatThrownBy(() -> access.requireReadableElder("family-a", 101L))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void deniesAccountsWhoseCurrentStatusOrFamilyRoleChanged() {
		assertThat(access.readableElderIds("family-a")).isNotEmpty();
		jdbc.update("UPDATE app_user SET enabled = FALSE WHERE id = 7");
		assertThatThrownBy(() -> access.readableElderIds("family-a")).isInstanceOf(AccessDeniedException.class);

		jdbc.update("UPDATE app_user SET enabled = TRUE WHERE id = 7");
		jdbc.update("DELETE FROM user_role WHERE user_id = 7");
		assertThatThrownBy(() -> access.requireReadableElder("family-a", 101L))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void distinguishesMissingFamilyProfilesFromValidFamiliesWithoutReadableElders() {
		assertThatThrownBy(() -> access.readableElderIds("no-profile")).isInstanceOf(AccessDeniedException.class);
		jdbc.update("UPDATE elder_family_binding SET status = 'REVOKED' WHERE family_member_id = 7");
		assertThat(access.readableElderIds("family-b")).isEmpty();
		assertThatThrownBy(() -> access.requireReadableElder("family-b", 110L))
				.isInstanceOf(AccessDeniedException.class);
	}

	private void seedBinding(long elderId, long familyId, String scope, String status, LocalDateTime expiresAt) {
		jdbc.update("""
				INSERT INTO elder_family_binding
				(elder_id, family_member_id, relationship, access_scope, status, confirmed_at, expires_at)
				VALUES (?, ?, 'DAUGHTER', ?, ?, ?, ?)
				""", elderId, familyId, scope, status, "ACTIVE".equals(status) ? NOW.minusDays(1) : null, expiresAt);
	}
}
