package sg.nus.carelink.profile.application;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row of the manager's certification register (UC-MG06), with the names the screen shows
 * resolved. {@code state} is CredentialRegisterPolicy.State; {@code watchedExpiry} is the date
 * the row is racing (the replaced certificate's expiry for a pending renewal), null when nothing
 * is due; {@code expiring} puts it in the Expiring filter.
 */
public record CredentialRegisterRow(
		Long id,
		Long caregiverId,
		String caregiverName,
		Long credentialTypeId,
		String credentialTypeName,
		boolean renewal,
		String state,
		LocalDate watchedExpiry,
		Long daysUntilExpiry,
		boolean expiring,
		String certificateNo,
		String issuingBody,
		LocalDate validFrom,
		LocalDate expiryDate,
		LocalDateTime submittedAt,
		String reviewNote,
		LocalDateTime reviewedAt,
		Long replacesId,
		LocalDate replacesExpiryDate) {
}
