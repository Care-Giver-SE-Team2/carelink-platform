package sg.nus.carelink.notification.domain.repository;

import java.util.Optional;
import sg.nus.carelink.notification.domain.model.InboxReader;

/** Resolves a currently enabled inbox reader; stale session roles do not grant access. */
public interface RecipientDirectory {

	/**
	 * Requires a current role and, for a family session, a current FAMILY role.
	 * Current FAMILY accounts always use family scope, even if the session predates that role.
	 */
	Optional<InboxReader> readerOf(String username, boolean familySession);
}
