package sg.nus.carelink.visit.controller.dto;

import java.time.LocalDate;

import sg.nus.carelink.profile.application.CaregiverPublicCredential;

/**
 * Exposes public credential details and their read-time status.
 *
 * @author Wang Zhili
 */
public record FamilyCredentialResponse(Long id, Long caregiverId, Long credentialTypeId,
		String credentialTypeName, String issuingBody, LocalDate validFrom, LocalDate expiryDate, String status) {

	public static FamilyCredentialResponse from(CaregiverPublicCredential credential) {
		return new FamilyCredentialResponse(credential.id(), credential.caregiverId(), credential.credentialTypeId(),
				credential.credentialTypeName(), credential.issuingBody(), credential.validFrom(),
				credential.expiryDate(), credential.status());
	}
}
