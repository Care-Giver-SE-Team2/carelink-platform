package sg.nus.carelink.profile.controller.dto;

import sg.nus.carelink.profile.domain.model.FamilyMember;

/** The new family profile; the client signs in with the username and password it just sent. */
public record FamilyRegistrationResponse(Long familyMemberId, String username, String fullName) {

	public static FamilyRegistrationResponse from(FamilyMember family, String username) {
		return new FamilyRegistrationResponse(family.id(), username, family.fullName());
	}
}
