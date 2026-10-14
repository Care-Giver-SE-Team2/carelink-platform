package sg.nus.carelink.profile.application;

import sg.nus.carelink.profile.domain.model.Caregiver;

/**
 * One caregiver as the picker sees them for one elder (UC-MG02): whether they may be named the
 * elder's primary caregiver and, when not, the rule that stops them and why, exactly as naming
 * them would be refused.
 *
 * @param refusalCode null when they may be named; otherwise CAREGIVER_NOT_ASSIGNABLE,
 *        CAREGIVER_MISSING_CREDENTIAL or CAREGIVER_TIME_CLASH
 * @param refusal the reason, ready to show the manager; null when they may be named
 */
public record CaregiverEligibility(Caregiver caregiver, String refusalCode, String refusal) {

	public boolean eligible() {
		return refusalCode == null;
	}
}
