package sg.nus.carelink.careplan.controller.dto;

import java.util.Arrays;
import java.util.List;

import sg.nus.carelink.careplan.domain.model.CareActivity;

/** One entry of GET /api/care-activities, in catalog order. */
public record CareActivityResponse(String code, String label, String category) {

	public static CareActivityResponse from(CareActivity activity) {
		return new CareActivityResponse(activity.code(), activity.label(), activity.category().label());
	}

	public static List<CareActivityResponse> catalog() {
		return Arrays.stream(CareActivity.values()).map(CareActivityResponse::from).toList();
	}
}
