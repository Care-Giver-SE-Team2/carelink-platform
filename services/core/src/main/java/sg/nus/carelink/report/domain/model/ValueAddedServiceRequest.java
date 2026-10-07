package sg.nus.carelink.report.domain.model;

import java.time.LocalDateTime;

/**
 * Domain model for value_added_service_request.
 *
 * <p>Generated starting point: the same fields as the table, and nothing else. This is
 * where the business rules and the design patterns go — reshape it into a proper
 * aggregate (add behaviour, fold child tables in, drop columns the domain does not
 * care about). identity.domain.model.AppUser is the template. Must not import JPA or
 * Spring Data; ArchUnit rejects the build if it does.
 */
public record ValueAddedServiceRequest(
		Long id,
		Long elderId,
		Long valueAddedServiceId,
		Long requestedByFamilyMemberId,
		Long approvingFamilyMemberId,
		Long visitId,
		LocalDateTime requestedSchedule,
		String specialInstructions,
		ValueAddedServiceRequest.Status status,
		LocalDateTime decidedAt,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {

	public enum Status {
		PENDING_APPROVAL, APPROVED, REJECTED, DISPATCHED, COMPLETED, CANCELLED
	}
}
