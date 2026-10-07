package sg.nus.carelink.profile.domain.model;

import java.util.List;

/**
 * Holds family-supplied intake details with required text validation and default values.
 * Applicant identity, review fields and timestamps are assigned by the server.
 *
 * @author Wang Zhili
 */
public record IntakeSubmission(
		String targetElderName,
		Integer targetElderAge,
		String targetAddress,
		String postalCode,
		IntakeApplication.MobilityLevel mobilityLevel,
		String preferredDialects,
		List<String> careNeeds,
		String medicalNotes) {

	public IntakeSubmission {
		targetElderName = requiredText(targetElderName, "targetElderName");
		targetAddress = requiredText(targetAddress, "targetAddress");
		postalCode = requiredText(postalCode, "postalCode");
		mobilityLevel = mobilityLevel == null ? IntakeApplication.MobilityLevel.INDEPENDENT : mobilityLevel;
		careNeeds = careNeeds == null ? List.of() : List.copyOf(careNeeds);
	}

	private static String requiredText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return value.strip();
	}
}
