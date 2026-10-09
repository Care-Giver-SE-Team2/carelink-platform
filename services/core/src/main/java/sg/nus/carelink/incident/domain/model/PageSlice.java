package sg.nus.carelink.incident.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * One page of results, in the only terms the domain is allowed to know: how many rows came
 * back, which page they are, and how many there are altogether.
 *
 * <p>Spring Data's {@code Page} says all of this and more, and it is deliberately not used
 * here. The domain layer may not depend on a persistence framework — ArchUnit fails the
 * build over it ({@code LayerDependencyTest}) — because the moment a port signature
 * mentions {@code Pageable} the whole escalation flow can only be tested with Spring on
 * the classpath. Four fields is the price of keeping that door shut.
 *
 * <p>The field names are the ones the manager's queue endpoint already publishes in
 * {@code docs/api/openapi-draft.yaml}, so the adapter translates once and nothing renames
 * anything on the way out.
 *
 * @param <T> what the page is a page of
 */
public record PageSlice<T>(List<T> items, int page, int size, long totalElements) {

	public PageSlice {
		items = List.copyOf(Objects.requireNonNull(items, "items"));
	}

	/** An empty page, for a request that asked past the end of the results. */
	public static <T> PageSlice<T> empty(int page, int size) {
		return new PageSlice<>(List.of(), page, size, 0);
	}
}
