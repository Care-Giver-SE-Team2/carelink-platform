package sg.nus.carelink.profile.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Domain model for elder.
 *
 * <p>Generated starting point: the same fields as the table, and nothing else. This is
 * where the business rules and the design patterns go — reshape it into a proper
 * aggregate (add behaviour, fold child tables in, drop columns the domain does not
 * care about). identity.domain.model.AppUser is the template. Must not import JPA or
 * Spring Data; ArchUnit rejects the build if it does.
 */
public record Elder(
		Long id,
		Long userId,
		String fullName,
		Elder.Gender gender,
		LocalDate dateOfBirth,
		String phone,
		String address,
		String postalCode,
		String sector,
		String preferredDialects,
		Boolean livesAlone,
		Elder.MobilityLevel mobilityLevel,
		Elder.ContinuityPreference continuityPreference,
		String medicalNotes,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	/** This elder with a login account linked to it. */
	public Elder withUserId(Long accountUserId) {
		return new Elder(id, accountUserId, fullName, gender, dateOfBirth, phone, address, postalCode, sector,
				preferredDialects, livesAlone, mobilityLevel, continuityPreference, medicalNotes, createdAt, updatedAt);
	}

	public enum Gender {
		MALE, FEMALE, OTHER
	}

	public enum MobilityLevel {
		INDEPENDENT, ASSISTIVE_CANE, WHEELCHAIR_BEDBOUND
	}

	public enum ContinuityPreference {
		PREFERRED, REQUIRED, NONE
	}
}
