package sg.nus.carelink.report.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A value-added service requested for a moment in the period (UC-EL02, UC-FM08).
 *
 * @param service      the catalogue name: Hospital escort, Companionship …
 * @param requestedFor when the elder asked for it
 * @param status       PENDING_APPROVAL, APPROVED, REJECTED, DISPATCHED, COMPLETED or CANCELLED
 * @param visitId      the visit it was dispatched as; null until it is
 */
public record ValueAddedFact(Long id, String service, LocalDateTime requestedFor, String status, Long visitId) {

	public ValueAddedFact {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(service, "service");
		Objects.requireNonNull(requestedFor, "requestedFor");
		Objects.requireNonNull(status, "status");
	}
}
