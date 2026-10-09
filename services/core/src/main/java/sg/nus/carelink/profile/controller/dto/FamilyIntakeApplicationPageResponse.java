package sg.nus.carelink.profile.controller.dto;

import java.util.List;

import sg.nus.carelink.profile.domain.model.IntakeApplicationPage;

/**
 * Exposes a page of family-visible applications and pagination metadata.
 *
 * @author Wang Zhili
 */
public record FamilyIntakeApplicationPageResponse(List<FamilyIntakeApplicationResponse> items,
		int page, int size, long totalElements) {

	public static FamilyIntakeApplicationPageResponse from(IntakeApplicationPage page) {
		return new FamilyIntakeApplicationPageResponse(
				page.items().stream().map(FamilyIntakeApplicationResponse::from).toList(),
				page.page(), page.size(), page.totalElements());
	}
}
