package sg.nus.carelink.profile.domain.model;

import java.util.List;

/**
 * A page of intake applications and the total number matching the query.
 *
 * @author Wang Zhili
 */
public record IntakeApplicationPage(List<IntakeApplication> items, int page, int size, long totalElements) {

	public IntakeApplicationPage {
		items = List.copyOf(items);
	}
}
