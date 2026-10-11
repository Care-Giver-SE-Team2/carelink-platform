package sg.nus.carelink.incident.infrastructure.notify;

import java.util.Optional;

/**
 * The caregiver on an elder's most recent visit, which visit knows: an incident alert reaches
 * them too. Asked of visit's internal API.
 */
interface LatestCaregivers {

	/** The caregiver on the elder's most recent visit that had one, or empty. */
	Optional<Long> latestCaregiverId(Long elderId);

}
