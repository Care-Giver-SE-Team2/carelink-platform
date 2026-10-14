package sg.nus.carelink.openplatform.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One try at a delivery: when, what the endpoint answered, and how long it took. An attempt that
 * got no answer - it timed out, or the connection or TLS failed - has no status and says why in
 * {@code error}. Only a 2xx delivers.
 */
public record DeliveryAttempt(
		String deliveryId,
		int attemptNo,
		Instant at,
		Integer responseStatus,
		long durationMs,
		String error) {

	public DeliveryAttempt {
		Objects.requireNonNull(deliveryId, "deliveryId");
		Objects.requireNonNull(at, "at");
		if (responseStatus == null && error == null) {
			throw new IllegalArgumentException("An attempt without an answer says why");
		}
	}

	public boolean delivered() {
		return responseStatus != null && responseStatus >= 200 && responseStatus < 300;
	}
}
