package sg.nus.carelink.openplatform.domain.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * One webhook to one provider, with what every attempt sends: the URL it was created for and the
 * body, kept so that each attempt repeats the same bytes. The id is the delivery ID the provider
 * deduplicates by, the same on every attempt and resend (docs/api/webhooks.md, sections 7 and 8).
 *
 * <p>A delivery is PENDING while an attempt is due, DELIVERED once one got a 2xx, and FAILED when
 * the attempts it may make are used up. A FAILED delivery can be resent: one more attempt, with
 * the same id and body.
 *
 * <p>Must not import JPA or Spring Data; ArchUnit rejects the build if it does.
 */
public record Delivery(
		String id,
		long providerId,
		WebhookEvent event,
		String jobId,
		URI url,
		String body,
		Status status,
		int attemptCount,
		int maxAttempts,
		Instant nextAttemptAt,
		Instant createdAt) {

	public enum Status {
		PENDING, DELIVERED, FAILED
	}

	public Delivery {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(event, "event");
		Objects.requireNonNull(url, "url");
		Objects.requireNonNull(body, "body");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(createdAt, "createdAt");
		if ((status == Status.PENDING) != (nextAttemptAt != null)) {
			throw new IllegalArgumentException("A delivery has a next attempt exactly while it is PENDING");
		}
	}

	/** A new delivery, due at once, allowed the retry schedule's attempts. */
	public static Delivery created(String id, long providerId, WebhookEvent event, String jobId, URI url, String body,
			Instant now) {
		return new Delivery(id, providerId, event, jobId, url, body, Status.PENDING, 0, RetrySchedule.MAX_ATTEMPTS,
				now, now);
	}

	/** The number the next attempt takes, from 1. */
	public int nextAttemptNo() {
		return attemptCount + 1;
	}

	/**
	 * The delivery after {@code attempt}: DELIVERED on a 2xx; otherwise due again after the retry
	 * schedule's wait, or FAILED when that was the last attempt it may make.
	 */
	public Delivery after(DeliveryAttempt attempt) {
		if (status != Status.PENDING) {
			throw new IllegalStateException("Only a PENDING delivery is attempted");
		}
		if (attempt.attemptNo() != nextAttemptNo()) {
			throw new IllegalArgumentException("Attempt " + attempt.attemptNo() + " is not this delivery's next");
		}
		int made = attempt.attemptNo();
		if (attempt.delivered()) {
			return with(Status.DELIVERED, made, maxAttempts, null);
		}
		if (made >= maxAttempts) {
			return with(Status.FAILED, made, maxAttempts, null);
		}
		return with(Status.PENDING, made, maxAttempts, attempt.at().plus(RetrySchedule.delayAfter(made)));
	}

	/** The provider asked for a failed delivery again: one more attempt, due at once. */
	public Delivery resent(Instant now) {
		if (status != Status.FAILED) {
			throw new BusinessRuleViolation("DELIVERY_NOT_FAILED", "Only a failed delivery can be resent.");
		}
		return with(Status.PENDING, attemptCount, attemptCount + 1, now);
	}

	/** CareLink stops sending it, without an attempt: the provider was suspended or removed its endpoint. */
	public Delivery abandoned() {
		if (status != Status.PENDING) {
			throw new IllegalStateException("Only a PENDING delivery is abandoned");
		}
		return with(Status.FAILED, attemptCount, attemptCount, null);
	}

	private Delivery with(Status nextStatus, int attempts, int allowed, Instant next) {
		return new Delivery(id, providerId, event, jobId, url, body, nextStatus, attempts, allowed, next, createdAt);
	}
}
