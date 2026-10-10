package sg.nus.carelink.report.application;

import java.util.Set;

/**
 * Which elders a family account may read or act for. core decides it from the account's current
 * bindings; report asks on every request and never keeps the answer. A method that refuses throws
 * {@code AccessDeniedException}.
 */
public interface FamilyAccessQuery {

	/** The elders the account may read now; empty when it may read none. */
	Set<Long> readableElderIds(String authenticatedUsername);

	void requireReadableElder(String authenticatedUsername, Long elderId);

	void requireWritableElder(String authenticatedUsername, Long elderId);

}
