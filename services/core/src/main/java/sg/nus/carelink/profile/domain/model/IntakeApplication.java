package sg.nus.carelink.profile.domain.model;

import java.time.LocalDateTime;
import java.util.List;

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

	public enum MobilityLevel {
		INDEPENDENT, ASSISTIVE_CANE, WHEELCHAIR_BEDBOUND
	}

	public enum Status {
		SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED
	}
}
