package sg.nus.carelink.careplan.controller.dto;

import java.math.BigDecimal;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import sg.nus.carelink.careplan.domain.model.CarePlanNode;

/**
 * A published task, in the shape the editor renders. Read-only: the editor doesn't round-trip a
 * plan for further edits (see CarePlanService.publish), it only redisplays the last published
 * version. groupName is a display-only label; the list itself is flat.
 *
 * <p>{@code visits} is the task's per-day schedule exactly as published: each day's own start
 * time and minutes, Monday first.
 */
public record CarePlanNodeResponse(
		Long id,
		String groupName,
		String name,
		List<VisitRequest> visits,
		CarePlanNode.EvidenceType evidenceType,
		BigDecimal weeklyHours) {

	/** The flat, published task list, in display order. */
	public static List<CarePlanNodeResponse> listFrom(List<CarePlanNode> nodes) {
		return nodes.stream()
				.sorted(Comparator.comparing(n -> n.displayOrder() == null ? 0 : n.displayOrder()))
				.map(CarePlanNodeResponse::of)
				.toList();
	}

	private static CarePlanNodeResponse of(CarePlanNode node) {
		return new CarePlanNodeResponse(
				node.id(), node.groupName(), node.name(), visitsFrom(node), node.evidenceType(), node.weeklyHours());
	}

	/** "Mon", "Tue", …: the labels the editor sends and ScheduleDays.dayOf reads back. */
	private static List<VisitRequest> visitsFrom(CarePlanNode node) {
		return node.visits().stream()
				.map(v -> new VisitRequest(
						v.day().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), v.startTime(), v.minutes()))
				.toList();
	}
}
