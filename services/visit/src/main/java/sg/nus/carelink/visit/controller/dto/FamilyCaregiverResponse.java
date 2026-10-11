package sg.nus.carelink.visit.controller.dto;

import java.util.List;

import sg.nus.carelink.visit.application.CaregiverDirectory.CaregiverPublicProfile;

/**
 * Contains only family-visible caregiver profile fields.
 *
 * @author Wang Zhili
 */
public record FamilyCaregiverResponse(Long id, String fullName, List<String> dialects) {

	public static FamilyCaregiverResponse from(CaregiverPublicProfile profile) {
		return new FamilyCaregiverResponse(profile.id(), profile.fullName(), profile.dialects());
	}
}
