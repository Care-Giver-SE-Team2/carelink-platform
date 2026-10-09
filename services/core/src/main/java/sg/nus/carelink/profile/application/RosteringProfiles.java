package sg.nus.carelink.profile.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cross-module contract for UC-MG04: the people a replacement decision is about. The caregivers
 * a replacement can be drawn from, the names of the certificates the search talks about, and
 * the elder a vacated visit is for. Rostering imports this interface only, never profile's
 * domain or infrastructure.
 */
public interface RosteringProfiles {

	/**
	 * Every caregiver who has not left, onboarding ones included: the search shows them as
	 * excluded, with the reason, rather than leaving the manager to wonder where they went.
	 */
	List<Candidate> candidates();

	/** Credential type id to its name, for the ids asked about that exist. */
	Map<Long, String> credentialTypeNames(Collection<Long> credentialTypeIds);

	Optional<ElderFacts> elder(Long elderId);

	/**
	 * A caregiver as rostering needs them.
	 *
	 * @param dialects as profile stores them, comma separated
	 * @param onboarding no published certificate yet, so not assignable
	 */
	record Candidate(Long caregiverId, Long userId, String fullName, String sector, String dialects,
			boolean onboarding) {
	}

	/** An elder as rostering needs them. */
	record ElderFacts(Long elderId, Long userId, String fullName, String sector, String preferredDialects) {
	}
}
