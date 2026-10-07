package sg.nus.carelink.identity.application;

import sg.nus.carelink.shared.security.Role;

/**
 * Cross-module contract: another module asks identity for a new login when its own use case
 * calls for one (e.g. approving a family's intake application creates the elder's login). The
 * caller never sees how accounts are stored or how passwords are hashed.
 */
public interface AccountIssuer {

	/**
	 * Creates an enabled account with one role, a username derived from {@code displayName},
	 * and a generated temporary password.
	 *
	 * @return the new account; {@code temporaryPassword} is the only copy in plain text, so the
	 *         caller shows it once and never stores it
	 */
	IssuedAccount issue(String displayName, Role role);

	record IssuedAccount(Long userId, String username, String temporaryPassword) {
	}
}
