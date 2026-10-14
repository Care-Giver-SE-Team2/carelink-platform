package sg.nus.carelink.profile.controller.dto;

import sg.nus.carelink.profile.application.CaregiverEligibility;
import sg.nus.carelink.profile.domain.model.Caregiver;

/**
 * One row of the manager's caregiver picker. assignable is Caregiver.isAssignable(), so the
 * client never re-derives the rule from status. Asked for one elder, assignable also takes in
 * that elder's certificates and timetable, and refusalCode and refusal say what stops them.
 */
public record CaregiverOptionResponse(
		Long id,
		String fullName,
		String sector,
		/** Comma-separated, as stored; the roster shows them under the caregiver's name. */
		String dialects,
		Caregiver.Status status,
		boolean assignable,
		/** CAREGIVER_NOT_ASSIGNABLE, CAREGIVER_MISSING_CREDENTIAL or CAREGIVER_TIME_CLASH; null when assignable. */
		String refusalCode,
		/** Why they can't be assigned, ready to show; null when assignable or not asked for an elder. */
		String refusal) {

	public static CaregiverOptionResponse from(Caregiver caregiver) {
		return new CaregiverOptionResponse(
				caregiver.id(),
				caregiver.fullName(),
				caregiver.sector(),
				caregiver.dialects(),
				caregiver.status(),
				caregiver.isAssignable(),
				caregiver.isAssignable() ? null : "CAREGIVER_NOT_ASSIGNABLE",
				null);
	}

	public static CaregiverOptionResponse from(CaregiverEligibility eligibility) {
		Caregiver caregiver = eligibility.caregiver();
		return new CaregiverOptionResponse(
				caregiver.id(),
				caregiver.fullName(),
				caregiver.sector(),
				caregiver.dialects(),
				caregiver.status(),
				eligibility.eligible(),
				eligibility.refusalCode(),
				eligibility.refusal());
	}
}
