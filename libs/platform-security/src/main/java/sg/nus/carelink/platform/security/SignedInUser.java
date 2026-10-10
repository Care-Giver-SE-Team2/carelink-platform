package sg.nus.carelink.platform.security;

import java.util.Set;

/**
 * The person behind the current request, as every service sees them.
 *
 * @param id the account id in core ({@code app_user.id})
 * @param username the name they signed in with
 * @param displayName the name to show
 * @param roles their roles without the {@code ROLE_} prefix, for example {@code MANAGER}
 */
public record SignedInUser(Long id, String username, String displayName, Set<String> roles) {

	public boolean hasRole(String role) {
		return roles.contains(role);
	}

}
