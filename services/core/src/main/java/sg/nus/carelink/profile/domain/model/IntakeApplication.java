package sg.nus.carelink.profile.domain.model;

import java.time.LocalDateTime;
import java.util.List;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * Represents a family's intake application and its review status.
 *
 * @author Wang Zhili
 */
public record IntakeApplication(
		Long id,
		Long applicantFamilyMemberId,
		String targetElderName,
		Integer targetElderAge,
		String targetAddress,
		String postalCode,
		IntakeApplication.MobilityLevel mobilityLevel,
		String preferredDialects,
		List<String> careNeeds,
		String medicalNotes,
		IntakeApplication.Status status,
		Long reviewedByUserId,
		String reviewRemarks,
		LocalDateTime createdAt,
		LocalDateTime reviewedAt,
		Long elderId) {

	public IntakeApplication {
		careNeeds = careNeeds == null ? List.of() : List.copyOf(careNeeds);
	}

	/**
	 * Create a SUBMITTED application with empty review fields and no linked elder.
	 *
	 * @param applicantFamilyMemberId Family profile identifier resolved from the authenticated account
	 * @param details Validated application details supplied by the family
	 * @return A new application whose identifier and creation time will be assigned by storage
	 * @throws IllegalArgumentException If the family profile identifier is null or not positive
	 *
	 * @author Wang Zhili
	 */
	public static IntakeApplication submit(Long applicantFamilyMemberId, IntakeSubmission details) {
		if (applicantFamilyMemberId == null || applicantFamilyMemberId <= 0) {
			throw new IllegalArgumentException("applicantFamilyMemberId must be positive");
		}
		return new IntakeApplication(null, applicantFamilyMemberId, details.targetElderName(),
				details.targetElderAge(), details.targetAddress(), details.postalCode(), details.mobilityLevel(),
				details.preferredDialects(), details.careNeeds(), details.medicalNotes(),
				Status.SUBMITTED, null, null, null, null, null);
	}

	/** Still waiting for a manager's answer. */
	public boolean isPending() {
		return status == Status.SUBMITTED || status == Status.UNDER_REVIEW;
	}

	/**
	 * The elder record an approval creates: the family's details as submitted, placed in
	 * {@code sector}. No account, date of birth or caregiver yet; those come later.
	 *
	 * @param sector Caregiver sector the postcode falls in; null when none is known yet
	 * @throws BusinessRuleViolation If the application has already been answered
	 */
	public Elder toElder(String sector) {
		requirePending();
		return new Elder(null, null, targetElderName, null, null, null, targetAddress, postalCode, sector,
				preferredDialects, null, Elder.MobilityLevel.valueOf(mobilityLevel.name()),
				Elder.ContinuityPreference.PREFERRED, medicalNotes, null, null);
	}

	/**
	 * Approves the application, linking it to the elder record created from it.
	 *
	 * @param remarks Optional message the family sees with the decision
	 * @throws BusinessRuleViolation If the application has already been answered
	 */
	public IntakeApplication approve(Long reviewerId, Long createdElderId, String remarks, LocalDateTime at) {
		requirePending();
		return reviewed(Status.APPROVED, reviewerId, blankToNull(remarks), at, createdElderId);
	}

	/**
	 * Declines the application. The family is told why, so a reason is required. Nothing is
	 * created; the application is kept with the reason.
	 *
	 * @throws BusinessRuleViolation If the reason is blank or the application has already been answered
	 */
	public IntakeApplication decline(Long reviewerId, String remarks, LocalDateTime at) {
		requirePending();
		if (blankToNull(remarks) == null) {
			throw new BusinessRuleViolation("DECLINE_REASON_REQUIRED", "A reason is required to decline an application");
		}
		return reviewed(Status.REJECTED, reviewerId, remarks.strip(), at, null);
	}

	private void requirePending() {
		if (!isPending()) {
			throw new BusinessRuleViolation("APPLICATION_ALREADY_ANSWERED",
					"Application " + id + " has already been " + status.name().toLowerCase());
		}
	}

	private IntakeApplication reviewed(Status outcome, Long reviewerId, String remarks, LocalDateTime at,
			Long createdElderId) {
		return new IntakeApplication(id, applicantFamilyMemberId, targetElderName, targetElderAge, targetAddress,
				postalCode, mobilityLevel, preferredDialects, careNeeds, medicalNotes, outcome, reviewerId, remarks,
				createdAt, at, createdElderId);
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	public enum MobilityLevel {
		INDEPENDENT, ASSISTIVE_CANE, WHEELCHAIR_BEDBOUND
	}

	public enum Status {
		SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED
	}
}
