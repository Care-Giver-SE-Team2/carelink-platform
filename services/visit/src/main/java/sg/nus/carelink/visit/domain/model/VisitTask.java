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

    public VisitTask result(Status result, String outcome, String note, LocalDateTime now) {
        if (status != Status.PENDING || result == null || result == Status.PENDING) throw new sg.nus.carelink.shared.error.BusinessRuleViolation("TASK_RESULT_NOT_ALLOWED", "Task cannot be changed.");
        String cleanOutcome = outcome == null ? null : outcome.strip();
        String cleanNote = note == null ? null : note.strip();
        if (result != Status.DONE && (cleanNote == null || cleanNote.isEmpty())) throw new sg.nus.carelink.shared.error.BusinessRuleViolation("TASK_REASON_REQUIRED", "A reason is required for skipped or refused tasks.");
        if ((cleanOutcome != null && cleanOutcome.length() > 255) || (cleanNote != null && cleanNote.length() > 500)) throw new sg.nus.carelink.shared.error.BusinessRuleViolation("TASK_TEXT_TOO_LONG", "Task text is too long.");
        return new VisitTask(id, visitId, carePlanNodeId, name, result, cleanOutcome, cleanNote, result == Status.DONE ? now : null);
    }
}
