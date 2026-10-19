package sg.nus.carelink.visit.application;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;

/**
 * What a caregiver's working day needs from core: their own profile, the elder they visit, and
 * the certificates that are about to lapse.
 */
public interface CaregiverWorkDirectory {

	/**
	 * The signed-in caregiver's profile.
	 *
	 * @throws org.springframework.security.access.AccessDeniedException when the account is not a caregiver's
	 */
	Profile require(String username);

	/** What a caregiver may know of an elder before a visit. */
	ElderView elder(Long elderId);

	/** The caregiver's certificates that lapse soon, as of {@code today}. */
	CredentialAlerts alerts(Long caregiverId, LocalDate today);

	record Profile(Long id, Long userId, String fullName, String phone, String sector, String dialects, String status) {

		/** Work access is separate from authentication and optional qualification uploads. */
		public Profile requireWorking() {
			if (!"AVAILABLE".equals(status) && !"BUSY".equals(status)) {
				throw new AccessDeniedException("Manager admission approval is required for caregiver work.");
			}
			return this;
		}

		/** An admitted caregiver, including one who has since left, may still read their own history. */
		public Profile requireAdmittedHistory() {
			if (!List.of("AVAILABLE", "BUSY", "INACTIVE").contains(status == null ? "" : status)) {
				throw new AccessDeniedException("Manager admission approval is required.");
			}
			return this;
		}

	}

	record ElderView(Long elderId, String preferredName, String serviceAddress, String postalSector,
			List<String> languageNeeds, String accessNotes, String emergencyNotes) {
	}

	record CredentialAlert(Long id, String name, String certificateNo, LocalDate expiryDate, String status, String warning,
			long daysUntilExpiry, String renewalState, LocalDate renewalValidFrom) {
	}

	record CredentialAlertContext(LocalDate asOfDate, int warningDays, boolean reviewRequired) {
	}

	record CredentialAlerts(List<CredentialAlert> items, CredentialAlertContext context) {
	}

}
