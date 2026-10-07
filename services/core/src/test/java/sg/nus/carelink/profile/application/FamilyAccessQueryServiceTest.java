package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.profile.domain.model.ElderFamilyBinding;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.repository.ElderFamilyBindingRepository;
import sg.nus.carelink.shared.security.Role;

/**
 * Verifies current family identity and readable elders through the application contract.
 *
 * @author Wang Zhili
 */
class FamilyAccessQueryServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 10, 0);
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-23T02:00:00Z"), ZoneOffset.UTC);

	private final UserDirectory users = mock(UserDirectory.class);
	private final InMemoryFamilyMemberRepository families = new InMemoryFamilyMemberRepository();
	private final ElderFamilyBindingRepository bindings = mock(ElderFamilyBindingRepository.class);
	private final FamilyAccessQuery service = new FamilyAccessQueryService(users, families, bindings, CLOCK);

	@BeforeEach
	void prepareAccounts() {
		when(users.findByUsername("family-a")).thenReturn(Optional.of(account(7L, Role.FAMILY, true)));
		when(users.findByUsername("family-b")).thenReturn(Optional.of(account(9L, Role.FAMILY, true)));
		when(users.findByUsername("manager")).thenReturn(Optional.of(account(10L, Role.MANAGER, true)));
		when(users.findByUsername("disabled")).thenReturn(Optional.of(account(11L, Role.FAMILY, false)));
		when(users.findByUsername("no-profile")).thenReturn(Optional.of(account(12L, Role.FAMILY, true)));
		families.save(new FamilyMember(42L, 7L, "Family A", null, null, null, null));
		families.save(new FamilyMember(7L, 9L, "Family B", null, null, null, null));
	}

	@Test
	void filtersBindingsUsingSingaporeTimeEvenWhenTheClockZoneIsUtc() {
		when(bindings.findByFamilyMemberId(42L)).thenReturn(List.of(
				binding(101L, ElderFamilyBinding.Status.ACTIVE, null),
				new ElderFamilyBinding(2L, 102L, 42L, ElderFamilyBinding.Relationship.SON, false,
						ElderFamilyBinding.AccessScope.READ_ONLY, ElderFamilyBinding.Status.ACTIVE,
						NOW.minusDays(1), NOW.plusSeconds(1), NOW.minusDays(1), NOW.minusDays(1)),
				binding(103L, ElderFamilyBinding.Status.ACTIVE, NOW),
				binding(104L, ElderFamilyBinding.Status.ACTIVE, NOW.minusSeconds(1)),
				binding(105L, ElderFamilyBinding.Status.PENDING_CONFIRMATION, null),
				binding(106L, ElderFamilyBinding.Status.REJECTED, null),
				binding(107L, ElderFamilyBinding.Status.REVOKED, null)));

		assertThat(service.readableElderIds("family-a")).containsExactlyInAnyOrder(101L, 102L);
	}

	@Test
	void resolvesBindingsByFamilyProfileIdRatherThanAccountId() {
		var own = binding(101L, ElderFamilyBinding.Status.ACTIVE, null);
		when(bindings.findByFamilyMemberId(42L)).thenReturn(List.of(own));
		when(bindings.findByFamilyMemberId(7L)).thenReturn(List.of(
				new ElderFamilyBinding(2L, 110L, 7L, ElderFamilyBinding.Relationship.SON, false,
						ElderFamilyBinding.AccessScope.FULL, ElderFamilyBinding.Status.ACTIVE,
						NOW.minusDays(1), null, NOW.minusDays(1), NOW.minusDays(1))));
		when(bindings.findByElderIdAndFamilyMemberId(101L, 42L)).thenReturn(Optional.of(own));

		assertThat(service.readableElderIds("family-a")).containsExactly(101L);
		assertThat(service.readableElderIds("family-b")).containsExactly(110L);
		service.requireReadableElder("family-a", 101L);
		assertThatThrownBy(() -> service.requireReadableElder("family-b", 101L))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void returnsAnEmptySetButRefusesExplicitAccessWithoutABinding() {
		assertThat(service.readableElderIds("family-a")).isEmpty();
		assertThatThrownBy(() -> service.requireReadableElder("family-a", 999L))
				.isInstanceOf(AccessDeniedException.class);
	}

	@ParameterizedTest
	@EnumSource(value = ElderFamilyBinding.Status.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
	void refusesAnExplicitElderWithAnInactiveBinding(ElderFamilyBinding.Status status) {
		when(bindings.findByElderIdAndFamilyMemberId(101L, 42L))
				.thenReturn(Optional.of(binding(101L, status, null)));

		assertThatThrownBy(() -> service.requireReadableElder("family-a", 101L))
				.isInstanceOf(AccessDeniedException.class);
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 0 })
	void refusesAnExplicitElderAtOrAfterExpiry(int expiryOffset) {
		when(bindings.findByElderIdAndFamilyMemberId(101L, 42L))
				.thenReturn(Optional.of(binding(101L, ElderFamilyBinding.Status.ACTIVE, NOW.plusSeconds(expiryOffset))));

		assertThatThrownBy(() -> service.requireReadableElder("family-a", 101L))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void rechecksBindingAccessOnEveryCall() {
		var active = binding(101L, ElderFamilyBinding.Status.ACTIVE, null);
		when(bindings.findByFamilyMemberId(42L)).thenReturn(List.of(active)).thenReturn(List.of(active.revoke()));
		when(bindings.findByElderIdAndFamilyMemberId(101L, 42L))
				.thenReturn(Optional.of(active)).thenReturn(Optional.of(active.revoke()));

		assertThat(service.readableElderIds("family-a")).containsExactly(101L);
		service.requireReadableElder("family-a", 101L);
		assertThat(service.readableElderIds("family-a")).isEmpty();
		assertThatThrownBy(() -> service.requireReadableElder("family-a", 101L))
				.isInstanceOf(AccessDeniedException.class);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "unknown", "manager", "disabled", "no-profile" })
	void deniesMissingOrIneligibleAccountsBeforeReadingBindings(String username) {
		assertThatThrownBy(() -> service.readableElderIds(username)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> service.requireReadableElder(username, 101L)).isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(bindings);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(longs = { 0L, -1L })
	void deniesInvalidElderIds(Long elderId) {
		assertThatThrownBy(() -> service.requireReadableElder("family-a", elderId))
				.isInstanceOf(AccessDeniedException.class);
		verifyNoInteractions(bindings);
	}

	private static AppUser account(Long id, Role role, boolean enabled) {
		return new AppUser(id, "account-" + id, "Family access test", Set.of(role), enabled);
	}

	private static ElderFamilyBinding binding(Long elderId, ElderFamilyBinding.Status status, LocalDateTime expiry) {
		return new ElderFamilyBinding(elderId, elderId, 42L, ElderFamilyBinding.Relationship.DAUGHTER,
				false, ElderFamilyBinding.AccessScope.FULL, status, NOW.minusDays(1), expiry,
				NOW.minusDays(1), NOW.minusDays(1));
	}
}
