package sg.nus.carelink.profile.domain.model;

import java.time.LocalDateTime;

/**
 * Domain model for caregiver.
 *
 * <p>Generated starting point: the same fields as the table, and nothing else. This is
 * where the business rules and the design patterns go — reshape it into a proper
 * aggregate (add behaviour, fold child tables in, drop columns the domain does not
 * care about). identity.domain.model.AppUser is the template. Must not import JPA or
 * Spring Data; ArchUnit rejects the build if it does.
 */
public record Caregiver(
		Long id,
		Long userId,
		String fullName,
		String phone,
		String sector,
		String dialects,
		Caregiver.Status status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	/**
	 * Whether a manager may name this caregiver as an elder's primary caregiver. ONBOARDING
	 * caregivers have no published credential yet and INACTIVE ones have left, so neither can
	 * take on an elder; BUSY only describes today's load and does not block.
	 */
	public boolean isAssignable() {
		return status == Status.AVAILABLE || status == Status.BUSY;
	}

	public enum Status {
		ONBOARDING, AVAILABLE, BUSY, INACTIVE
	}
}
