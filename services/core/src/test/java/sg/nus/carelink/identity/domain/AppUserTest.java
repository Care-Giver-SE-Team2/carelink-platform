package sg.nus.carelink.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.shared.security.Role;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reference example of a domain unit test.
 *
 * <p>There is no SpringBootTest, no database and no container: the domain object is
 * simply instantiated and asserted on. The whole class runs in milliseconds. Domain
 * tests in every module should look like this.
 */
class AppUserTest {

	@Test
	@DisplayName("the role set is immutable to callers, so permissions cannot be changed behind the rules")
	void roleSetIsImmutable() {
		AppUser user = new AppUser(1L, "alice", "Alice", Set.of(Role.CAREGIVER), true);
		Set<Role> roles = user.roles();

		assertThatThrownBy(() -> roles.add(Role.MANAGER))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("hasRole reports role membership")
	void reportsRoleMembership() {
		AppUser user = new AppUser(1L, "bob", "Bob", Set.of(Role.MANAGER, Role.CAREGIVER), true);

		assertThat(user.hasRole(Role.MANAGER)).isTrue();
		assertThat(user.hasRole(Role.CAREGIVER)).isTrue();
		assertThat(user.hasRole(Role.FAMILY)).isFalse();
	}

	@Test
	@DisplayName("username and display name are mandatory")
	void mandatoryFieldsAreChecked() {
		assertThatThrownBy(() -> new AppUser(1L, null, "X", Set.of(), true))
				.isInstanceOf(NullPointerException.class)
				.hasMessage("username");
	}

	@Test
	@DisplayName("Spring Security authorities carry the ROLE_ prefix")
	void authoritiesCarryPrefix() {
		assertThat(Role.MANAGER.authority()).isEqualTo("ROLE_MANAGER");
		assertThat(Role.ELDER.authority()).isEqualTo("ROLE_ELDER");
	}
}
