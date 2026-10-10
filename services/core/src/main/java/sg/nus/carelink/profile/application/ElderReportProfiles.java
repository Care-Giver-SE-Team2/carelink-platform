package sg.nus.carelink.profile.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * What a report says about an elder, read in one go: the profile, the primary caregiver and the
 * latest published plan. report asks for it once per elder when it writes the week's reports, so
 * it no longer reads profile's and careplan's tables itself.
 */
public interface ElderReportProfiles {

	Optional<Profile> find(Long elderId);

	/**
	 * @param planVersion null when the elder has no published plan
	 * @param planWeeklyHours null when the elder has no published plan
	 */
	record Profile(Long elderId, String fullName, String gender, LocalDate dateOfBirth, String mobilityLevel,
			Boolean livesAlone, String medicalNotes, Long primaryCaregiverId, String primaryCaregiverName,
			Integer planVersion, BigDecimal planWeeklyHours) {
	}

}
