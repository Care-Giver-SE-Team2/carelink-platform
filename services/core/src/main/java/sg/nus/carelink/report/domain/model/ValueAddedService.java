package sg.nus.carelink.report.domain.model;

import java.time.LocalDateTime;

/**
 * Domain model for value_added_service.
 *
 * <p>Generated starting point: the same fields as the table, and nothing else. This is
 * where the business rules and the design patterns go — reshape it into a proper
 * aggregate (add behaviour, fold child tables in, drop columns the domain does not
 * care about). identity.domain.model.AppUser is the template. Must not import JPA or
 * Spring Data; ArchUnit rejects the build if it does.
 */
public record ValueAddedService(
		Long id,
		String name,
		String description,
		ValueAddedService.Status status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public enum Status {
		AVAILABLE, UNAVAILABLE
	}
}
