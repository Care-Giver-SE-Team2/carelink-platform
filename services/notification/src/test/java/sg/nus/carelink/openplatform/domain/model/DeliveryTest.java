package sg.nus.carelink.openplatform.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * A delivery's life on the retry schedule (docs/api/webhooks.md, section 7): due at once, then
 * after 1, 5, 30, 120 and 360 minutes, FAILED after the sixth failure, DELIVERED on the first 2xx,
 * and one more attempt for each resend. Its id and body never change.
 */
class DeliveryTest {

	private static final Instant CREATED = Instant.parse("2026-10-15T01:00:00Z");

	private static Delivery created() {
		return Delivery.created("dlv_test", 509, WebhookEvent.JOB_DISPATCHED, "JOB-24081",
				URI.create("https://hooks.example.sg/carelink"), "{}", CREATED);
	}

	private static DeliveryAttempt failed(Delivery delivery, Instant at) {
		return new DeliveryAttempt(delivery.id(), delivery.nextAttemptNo(), at, 503, 40, null);
	}

	@Test
	void aNewDeliveryIsDueAtOnceWithSixAttempts() {
		Delivery delivery = created();

		assertThat(delivery.status()).isEqualTo(Delivery.Status.PENDING);
		assertThat(delivery.nextAttemptAt()).isEqualTo(CREATED);
		assertThat(delivery.attemptCount()).isZero();
		assertThat(delivery.maxAttempts()).isEqualTo(6);
	}

	@Test
	void eachFailureWaitsLongerOnTheScheduleAndTheSixthGivesUp() {
		Delivery delivery = created();
		Instant at = CREATED;
		Duration[] waits = { Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
				Duration.ofHours(2), Duration.ofHours(6) };

		for (Duration wait : waits) {
			delivery = delivery.after(failed(delivery, at));
			assertThat(delivery.status()).isEqualTo(Delivery.Status.PENDING);
			assertThat(delivery.nextAttemptAt()).isEqualTo(at.plus(wait));
			at = delivery.nextAttemptAt();
		}
		delivery = delivery.after(failed(delivery, at));

		assertThat(delivery.status()).isEqualTo(Delivery.Status.FAILED);
		assertThat(delivery.attemptCount()).isEqualTo(6);
		assertThat(delivery.nextAttemptAt()).isNull();
		// The last attempt is 8 h 36 min after the first, as the spec's table says
		assertThat(Duration.between(CREATED, at)).isEqualTo(Duration.ofMinutes(516));
	}

	@Test
	void anyTwoHundredDeliversAndNothingFollows() {
		Delivery delivery = created();
		delivery = delivery.after(failed(delivery, CREATED));

		Delivery delivered = delivery.after(
				new DeliveryAttempt(delivery.id(), 2, CREATED.plusSeconds(60), 204, 80, null));

		assertThat(delivered.status()).isEqualTo(Delivery.Status.DELIVERED);
		assertThat(delivered.attemptCount()).isEqualTo(2);
		assertThat(delivered.nextAttemptAt()).isNull();
	}

	@Test
	void redirectsTimeoutsAndClientErrorsAllFail() {
		Delivery delivery = created();

		assertThat(delivery.after(new DeliveryAttempt(delivery.id(), 1, CREATED, 302, 10, null)).status())
				.isEqualTo(Delivery.Status.PENDING);
		assertThat(delivery.after(new DeliveryAttempt(delivery.id(), 1, CREATED, 401, 10, null)).status())
				.isEqualTo(Delivery.Status.PENDING);
		assertThat(delivery.after(new DeliveryAttempt(delivery.id(), 1, CREATED, null, 10_000, "TIMEOUT")).status())
				.isEqualTo(Delivery.Status.PENDING);
	}

	@Test
	void aResentFailedDeliveryGetsOneMoreAttemptWithTheSameIdAndBody() {
		Delivery delivery = exhausted();
		Instant later = CREATED.plus(Duration.ofDays(1));

		Delivery resent = delivery.resent(later);

		assertThat(resent.id()).isEqualTo(delivery.id());
		assertThat(resent.body()).isEqualTo(delivery.body());
		assertThat(resent.status()).isEqualTo(Delivery.Status.PENDING);
		assertThat(resent.nextAttemptAt()).isEqualTo(later);
		assertThat(resent.nextAttemptNo()).isEqualTo(7);

		Delivery failedAgain = resent.after(failed(resent, later));
		assertThat(failedAgain.status()).isEqualTo(Delivery.Status.FAILED);
		assertThat(failedAgain.attemptCount()).isEqualTo(7);
	}

	@Test
	void onlyAFailedDeliveryCanBeResent() {
		assertThatThrownBy(() -> created().resent(CREATED))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageContaining("Only a failed delivery");
	}

	@Test
	void anAbandonedDeliveryFailsWithoutAnotherAttempt() {
		Delivery delivery = created();
		delivery = delivery.after(failed(delivery, CREATED));

		Delivery abandoned = delivery.abandoned();

		assertThat(abandoned.status()).isEqualTo(Delivery.Status.FAILED);
		assertThat(abandoned.attemptCount()).isEqualTo(1);
		assertThat(abandoned.nextAttemptAt()).isNull();
	}

	@Test
	void anAttemptMustBeTheDeliverysNext() {
		Delivery delivery = created();

		assertThatThrownBy(() -> delivery.after(new DeliveryAttempt(delivery.id(), 2, CREATED, 200, 1, null)))
				.isInstanceOf(IllegalArgumentException.class);
		Delivery delivered = delivery.after(new DeliveryAttempt(delivery.id(), 1, CREATED, 200, 1, null));
		assertThatThrownBy(() -> delivered.after(new DeliveryAttempt(delivery.id(), 2, CREATED, 200, 1, null)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void anAttemptWithoutAnAnswerSaysWhy() {
		assertThatThrownBy(() -> new DeliveryAttempt("dlv_test", 1, CREATED, null, 5, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aResentDeliverysLaterAttemptsWaitAsLongAsTheLast() {
		assertThat(RetrySchedule.delayAfter(5)).isEqualTo(Duration.ofHours(6));
		assertThat(RetrySchedule.delayAfter(9)).isEqualTo(Duration.ofHours(6));
		assertThatThrownBy(() -> RetrySchedule.delayAfter(0)).isInstanceOf(IllegalArgumentException.class);
	}

	private static Delivery exhausted() {
		Delivery delivery = created();
		while (delivery.status() == Delivery.Status.PENDING) {
			delivery = delivery.after(failed(delivery, delivery.nextAttemptAt()));
		}
		return delivery;
	}
}
