package sg.nus.carelink.visit.controller.dto;

import java.util.List;

import sg.nus.carelink.visit.application.FamilyVisitSchedule;

/**
 * Exposes a family schedule page and the matching total count.
 *
 * @author Wang Zhili
 */
public record FamilyVisitPageResponse(List<FamilyVisitResponse> items, int page, int size, long totalElements) {

	public static FamilyVisitPageResponse from(FamilyVisitSchedule schedule) {
		var page = schedule.visits();
		return new FamilyVisitPageResponse(
				page.items().stream().map(visit -> FamilyVisitResponse.from(visit, schedule.asOf())).toList(),
				page.page(), page.size(), page.totalElements());
	}
}
