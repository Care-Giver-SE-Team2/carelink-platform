package sg.nus.carelink.visit.controller.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import sg.nus.carelink.visit.domain.model.VisitStateTransition;

/**
 * Family timeline fields, excluding internal actors and rejection details.
 *
 * @author Wang Zhili
 */
public record FamilyVisitTimelineEntryResponse(Long id, Long visitId, String fromState, String toState,
		VisitStateTransition.Result result, OffsetDateTime occurredAt) {

	public static FamilyVisitTimelineEntryResponse from(VisitStateTransition transition) {
		return new FamilyVisitTimelineEntryResponse(transition.id(), transition.visitId(), transition.fromState(),
				transition.toState(), transition.result(),
				transition.occurredAt().atZone(ZoneId.of("Asia/Singapore")).toOffsetDateTime());
	}
}
