package sg.nus.carelink.report.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * One page of reports, in the only terms the domain is allowed to know.
 *
 * <p>The same four fields as the incident module's {@code PageSlice}, and deliberately not
 * that class: the report module may not import the incident module (the slice rule in
 * {@code LayerDependencyTest}), and moving {@code PageSlice} into {@code shared} would mean
 * changing a package every other module is built on for the sake of this one. Two copies of
 * a four-field record are the cheaper of the two until somebody does that move on purpose.
 *
 * <p>Not Spring Data's {@code Page} either, for the reason {@code PageSlice} gives: a port
 * that mentions a persistence framework is a port that can only be tested with it.
 */
public record ReportPage(List<Report> items, int page, int size, long totalElements) {

	public ReportPage {
		items = List.copyOf(Objects.requireNonNull(items, "items"));
	}
}
