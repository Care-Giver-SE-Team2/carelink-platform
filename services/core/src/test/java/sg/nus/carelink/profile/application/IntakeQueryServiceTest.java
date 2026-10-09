package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.model.IntakeSubmission;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;

/**
 * Verifies current family access and application ownership at the query service boundary.
 *
 * @author Wang Zhili
 */
class IntakeQueryServiceTest {

	private final Map<String, AppUser> accounts = Map.of(
			"family-a", new AppUser(7L, "family-a", "Family A", Set.of(Role.FAMILY), true),
			"manager", new AppUser(10L, "manager", "Manager", Set.of(Role.MANAGER), true),
			"disabled", new AppUser(11L, "disabled", "Disabled family", Set.of(Role.FAMILY), false),
			"no-profile", new AppUser(12L, "no-profile", "Missing profile", Set.of(Role.FAMILY), true));
	private final UserDirectory users = new UserDirectory() {
		@Override
		public Optional<AppUser> findByUsername(String username) {
			return Optional.ofNullable(accounts.get(username));
		}

		@Override
		public Optional<AppUser> findById(Long id) {
			return accounts.values().stream().filter(account -> account.id().equals(id)).findFirst();
		}
	};
	private final InMemoryFamilyMemberRepository families = new InMemoryFamilyMemberRepository();
	private final InMemoryIntakeApplicationRepository applications = new InMemoryIntakeApplicationRepository();
	private final IntakeQueryService service = new IntakeQueryService(users, families, applications);

	@BeforeEach
	void prepareProfiles() {
		families.save(new FamilyMember(42L, 7L, "Family A", null, null, null, null));
		families.save(new FamilyMember(7L, 9L, "Family B", null, null, null, null));
		families.save(new FamilyMember(53L, 10L, "Manager", null, null, null, null));
		families.save(new FamilyMember(54L, 11L, "Disabled family", null, null, null, null));
	}

	@Test
	void resolvesApplicationOwnershipThroughTheFamilyProfileWithoutAnElderBinding() {
		var details = new IntakeSubmission("Tan Mei", null, "12 Example Road", "123456", null, null, null, null);
		var own = applications.save(IntakeApplication.submit(42L, details));
		applications.save(IntakeApplication.submit(7L, details));

		var result = service.listMine("family-a", IntakeApplication.Status.SUBMITTED, 0, 20);

		assertThat(result.items()).containsExactly(own);
		assertThat(result.totalElements()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(longs = { 0L, -1L, 999L, Long.MAX_VALUE })
	void reportsANonexistentApplicationAfterCheckingFamilyAccess(Long applicationId) {
		assertThatThrownBy(() -> service.getMine("family-a", applicationId))
				.isInstanceOf(ResourceNotFound.class);
	}

	@Test
	void readsTheCurrentFamilysApplicationWithoutRequiringAnElderBinding() {
		var details = new IntakeSubmission("Tan Mei", null, "12 Example Road", "123456", null, null, null, null);
		var saved = applications.save(IntakeApplication.submit(42L, details));

		assertThat(service.getMine("family-a", saved.id())).isEqualTo(saved);
	}

	@Test
	void refusesAnApplicationOwnedByAnotherFamilyEvenWhenItsFamilyIdMatchesTheUserId() {
		var details = new IntakeSubmission("Private elder", null, "Private address", "123456", null, null, null, null);
		var other = applications.save(IntakeApplication.submit(7L, details));

		assertThatThrownBy(() -> service.getMine("family-a", other.id()))
				.isInstanceOf(AccessDeniedException.class);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "unknown", "manager", "disabled", "no-profile" })
	void refusesQueriesWithoutAnEnabledFamilyAccountAndProfile(String username) {
		assertThatThrownBy(() -> service.listMine(username, null, 0, 20))
				.isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> service.getMine(username, 999L))
				.isInstanceOf(AccessDeniedException.class);
	}
}
