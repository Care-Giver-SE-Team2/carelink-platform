package sg.nus.carelink.visit.domain.model;

import java.util.List;

/**
 * A page of visits and the count after all access and search filters.
 *
 * @author Wang Zhili
 */
public record VisitPage(List<Visit> items, int page, int size, long totalElements) {
	public VisitPage {
		items = List.copyOf(items);
	}
}
