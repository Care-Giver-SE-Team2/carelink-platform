package sg.nus.carelink.profile.application;

import java.util.Set;

/**
 * Provides current family read access to other application modules.
 *
 * @author Wang Zhili
 */
public interface FamilyAccessQuery {

	/**
	 * Finds the elders the authenticated family can currently read.
	 *
	 * @param authenticatedUsername Username supplied by the authenticated principal
	 * @return Immutable elder IDs; empty when no binding currently grants access
	 * @throws org.springframework.security.access.AccessDeniedException If current family access is unavailable
	 * @author Wang Zhili
	 */
	Set<Long> readableElderIds(String authenticatedUsername);

	/**
	 * Requires a current readable binding between the authenticated family and an elder.
	 *
	 * @param authenticatedUsername Username supplied by the authenticated principal
	 * @param elderId Elder whose care resources will be read
	 * @throws org.springframework.security.access.AccessDeniedException If family or elder access is unavailable
	 * @author Wang Zhili
	 */
	void requireReadableElder(String authenticatedUsername, Long elderId);
}
