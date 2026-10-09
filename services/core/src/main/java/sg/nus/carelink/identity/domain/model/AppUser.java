package sg.nus.carelink.identity.domain.model;

import sg.nus.carelink.shared.security.Role;

import java.util.Objects;
import java.util.Set;

/**
 * Domain model of an account.
 *
 * <p>It deliberately holds no password hash: a password is an implementation detail of
 * the authentication mechanism and belongs in infrastructure. The domain layer only
 * cares who this is and what they are allowed to do.
 *
 * <p>A record, so the invariants are the only code here. The compact constructor
 * rejects missing values and replaces the role set with an immutable copy, which means
 * a caller cannot reach in and grant themselves a role after construction.
 *
 * <p>No framework annotations, so this can simply be instantiated in a test.
 */
public record AppUser(Long id, String username, String displayName, Set<Role> roles, boolean enabled) {

	public AppUser {
		Objects.requireNonNull(username, "username");
		Objects.requireNonNull(displayName, "displayName");
		roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
	}

	public boolean hasRole(Role role) {
		return roles.contains(role);
	}
}
