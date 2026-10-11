package sg.nus.carelink.incident.domain.repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Which incident each keyed request raised, so a request that arrives again - a retry after its answer
 * was lost - gets the same incident instead of a second one.
 */
public interface IncidentRequestKeys {

	Optional<Long> incidentFor(String requestKey);

	/** Fails with a duplicate key when another request with the same key got there first. */
	void remember(String requestKey, Long incidentId, LocalDateTime at);
}
