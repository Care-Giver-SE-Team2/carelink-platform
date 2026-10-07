package sg.nus.carelink.profile.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies binding read scopes, inactive states and the exact expiry boundary.
 *
 * @author Wang Zhili
 */
class ElderFamilyBindingAccessTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 10, 0);

	@ParameterizedTest
	@EnumSource(ElderFamilyBinding.AccessScope.class)
	void permitsBothReadScopesWithoutAnExpiry(ElderFamilyBinding.AccessScope scope) {
		assertThat(binding(ElderFamilyBinding.Status.ACTIVE, scope, null).allowsReadAt(NOW)).isTrue();
	}

	@Test
	void permitsAccessBeforeExpiry() {
		assertThat(binding(ElderFamilyBinding.Status.ACTIVE, ElderFamilyBinding.AccessScope.FULL,
				NOW.plusSeconds(1)).allowsReadAt(NOW)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 0 })
	void deniesAccessAtOrAfterExpiry(int expiryOffset) {
		assertThat(binding(ElderFamilyBinding.Status.ACTIVE, ElderFamilyBinding.AccessScope.READ_ONLY,
				NOW.plusSeconds(expiryOffset)).allowsReadAt(NOW)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = ElderFamilyBinding.Status.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
	void deniesInactiveBindingsEvenWithNoExpiry(ElderFamilyBinding.Status status) {
		assertThat(binding(status, ElderFamilyBinding.AccessScope.FULL, null).allowsReadAt(NOW)).isFalse();
	}

	@Test
	void doesNotGrantAccessWithoutAReadScope() {
		assertThat(binding(ElderFamilyBinding.Status.ACTIVE, null, null).allowsReadAt(NOW)).isFalse();
	}

	@Test
	void requiresAnExplicitAccessTime() {
		var active = binding(ElderFamilyBinding.Status.ACTIVE, ElderFamilyBinding.AccessScope.FULL, null);
		assertThatNullPointerException().isThrownBy(() -> active.allowsReadAt(null));
	}

	private static ElderFamilyBinding binding(ElderFamilyBinding.Status status,
			ElderFamilyBinding.AccessScope scope, LocalDateTime expiresAt) {
		return new ElderFamilyBinding(1L, 101L, 42L, ElderFamilyBinding.Relationship.DAUGHTER,
				false, scope, status, NOW.minusDays(1), expiresAt, NOW.minusDays(1), NOW.minusDays(1));
	}
}
