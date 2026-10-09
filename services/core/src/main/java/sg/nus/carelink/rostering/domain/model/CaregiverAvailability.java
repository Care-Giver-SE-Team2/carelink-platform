package sg.nus.carelink.rostering.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Domain model for caregiver_availability.
 *
 * <p>Generated starting point: the same fields as the table, and nothing else. This is
 * where the business rules and the design patterns go — reshape it into a proper
 * aggregate (add behaviour, fold child tables in, drop columns the domain does not
 * care about). identity.domain.model.AppUser is the template. Must not import JPA or
 * Spring Data; ArchUnit rejects the build if it does.
 */
public record CaregiverAvailability(
		Long id,
		Long caregiverId,
		LocalDate availableDate,
		LocalTime availableStart,
		LocalTime availableEnd,
		CaregiverAvailability.Status status,
		LocalDateTime createdAt) {

	public enum Status {
		AVAILABLE, UNAVAILABLE
	}
}
