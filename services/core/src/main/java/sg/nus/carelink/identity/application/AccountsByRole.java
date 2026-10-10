package sg.nus.carelink.identity.application;

import java.util.List;

import sg.nus.carelink.shared.security.Role;

/**
 * Who currently holds a role: the enabled accounts with it. Another service uses it to address a
 * message to, say, every manager, instead of reading identity's tables.
 */
public interface AccountsByRole {

	/** The ids of the enabled accounts that hold the role, in id order. */
	List<Long> enabledIdsWithRole(Role role);

}
