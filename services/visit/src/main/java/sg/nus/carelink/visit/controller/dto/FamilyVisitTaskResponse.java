package sg.nus.carelink.visit.controller.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import sg.nus.carelink.visit.domain.model.VisitTask;

/**
 * Family task progress, excluding plan references, outcomes and caregiver notes.
 *
 * @author Wang Zhili
 */
public record FamilyVisitTaskResponse(Long id, Long visitId, String name, VisitTask.Status status,
		OffsetDateTime completedAt) {

	public static FamilyVisitTaskResponse from(VisitTask task) {
		return new FamilyVisitTaskResponse(task.id(), task.visitId(), task.name(), task.status(),
				task.completedAt() == null ? null : task.completedAt().atZone(ZoneId.of("Asia/Singapore")).toOffsetDateTime());
	}
}
