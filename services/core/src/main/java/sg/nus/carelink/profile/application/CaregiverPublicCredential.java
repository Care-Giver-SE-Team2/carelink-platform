package sg.nus.carelink.profile.application;

import java.time.LocalDate;

/**
 * Carries public credential details without certificate numbers or review metadata.
 *
 * @author Wang Zhili
 */
public record CaregiverPublicCredential(Long id, Long caregiverId, Long credentialTypeId,
		String credentialTypeName, String issuingBody, LocalDate validFrom, LocalDate expiryDate, String status) {
}
