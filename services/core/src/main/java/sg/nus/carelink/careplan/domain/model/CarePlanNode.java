package sg.nus.carelink.careplan.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Domain model for care_plan_node.
 *
 * <p>Generated starting point: the same fields as the table, and nothing else. This is
 * where the business rules and the design patterns go — reshape it into a proper
 * aggregate (add behaviour, fold child tables in, drop columns the domain does not
 * care about). identity.domain.model.AppUser is the template. Must not import JPA or
 * Spring Data; ArchUnit rejects the build if it does.
 */
public record CarePlanNode(
		Long id,
		Long carePlanId,
		String groupName,
		/** The CareActivity code this task delivers; null for a task outside the catalog. */
		String activityCode,
		String name,
		String scheduleDays,
		BigDecimal durationPerVisit,
		BigDecimal weeklyHours,
		CarePlanNode.EvidenceType evidenceType,
		Integer displayOrder,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		/** One entry per scheduled day, Monday first. scheduleDays and weeklyHours summarise it. */
		List<ScheduledVisit> visits) {

	public CarePlanNode {
		visits = visits == null
				? List.of()
				: visits.stream().sorted(Comparator.comparing(ScheduledVisit::day)).toList();
	}

	public enum EvidenceType {
		NONE, CHECKLIST, PHOTO, READING
	}
}
