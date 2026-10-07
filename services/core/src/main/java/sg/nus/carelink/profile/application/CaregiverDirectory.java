package sg.nus.carelink.profile.application;

import java.util.List;
import java.util.Optional;

/**
 * Provides public caregiver data to authorized application use cases.
 *
 * @author Wang Zhili
 */
public interface CaregiverDirectory {

	/**
	 * Finds public details after the caller has checked resource access.
	 *
	 * @param caregiverId Caregiver profile identifier
	 * @return Public profile, or empty when the caregiver does not exist
	 * @author Wang Zhili
	 */
	Optional<CaregiverPublicProfile> findPublicProfile(Long caregiverId);

	/**
	 * Lists public credentials after the caller has checked resource access.
	 *
	 * @param caregiverId Authorized caregiver profile identifier
	 * @return Public credentials ordered by credential type and record ID
	 * @throws sg.nus.carelink.shared.error.ResourceNotFound If the caregiver profile is missing
	 * @author Wang Zhili
	 */
	List<CaregiverPublicCredential> listPublicCredentials(Long caregiverId);
}
