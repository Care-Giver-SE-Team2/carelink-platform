package sg.nus.carelink.profile.application;

import java.util.Optional;

/**
 * Cross-module contract for UC-MG03: who an elder's new visits go to by default. Rostering
 * imports this interface only, never profile's domain or infrastructure.
 */
public interface PrimaryCaregiverLookup {

	/**
	 * The elder's primary caregiver, if one is named and may currently take visits (not
	 * onboarding or inactive). Empty means new visits are left unassigned.
	 */
	Optional<Long> findRosterableCaregiverId(Long elderId);

	/**
	 * The elder's primary caregiver when one is named but can't take visits now (onboarding or
	 * inactive), so the visits they already hold can be released to be covered. Empty when there
	 * is none, or they can.
	 */
	Optional<Long> findUnrosterableCaregiverId(Long elderId);
}
