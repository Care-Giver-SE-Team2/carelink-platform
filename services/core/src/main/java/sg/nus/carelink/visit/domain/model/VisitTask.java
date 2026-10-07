package sg.nus.carelink.visit.domain.model;

import java.time.LocalDateTime;

/**
 * Domain model for visit_task.
 *
 * <p>Generated starting point: the same fields as the table, and nothing else. This is
 * where the business rules and the design patterns go — reshape it into a proper
 * aggregate (add behaviour, fold child tables in, drop columns the domain does not
 * care about). identity.domain.model.AppUser is the template. Must not import JPA or
 * Spring Data; ArchUnit rejects the build if it does.
 */
public record VisitTask(
		Long id,
		Long visitId,
		Long carePlanNodeId,
		String name,
		VisitTask.Status status,
		String outcome,
		String caregiverNote,
		LocalDateTime completedAt) {

	public enum Status {
		PENDING, DONE, SKIPPED, REFUSED
	}
}
