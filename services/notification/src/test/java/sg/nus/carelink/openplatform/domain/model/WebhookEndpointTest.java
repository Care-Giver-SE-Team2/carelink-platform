package sg.nus.carelink.openplatform.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

class WebhookEndpointTest {

	private static final URI URL = URI.create("https://hooks.example.sg/carelink");

	private static final Instant NOW = Instant.parse("2026-10-15T01:00:00Z");

	@Test
	void receivesWhatItSubscribedToAndEveryPing() {
		WebhookEndpoint endpoint = WebhookEndpoint.of(509, URL, Set.of(WebhookEvent.JOB_DISPATCHED), "ref-1");

		assertThat(endpoint.receives(WebhookEvent.JOB_DISPATCHED)).isTrue();
		assertThat(endpoint.receives(WebhookEvent.JOB_CANCELLED)).isFalse();
		assertThat(endpoint.receives(WebhookEvent.PING)).isTrue();
	}

	@Test
	void aDisabledEndpointReceivesNothingNotEvenAPing() {
		WebhookEndpoint suspended = new WebhookEndpoint(509, URL, Set.of(WebhookEvent.JOB_DISPATCHED), "ref-1", null,
				null, false);

		assertThat(suspended.receives(WebhookEvent.JOB_DISPATCHED)).isFalse();
		assertThat(suspended.receives(WebhookEvent.PING)).isFalse();
	}

	@Test
	void signsWithThePreviousSecretTooOnlyDuringTheOverlap() {
		WebhookEndpoint rotated = new WebhookEndpoint(509, URL, Set.of(), "ref-new", "ref-old",
				NOW.plus(Duration.ofHours(24)), true);

		assertThat(rotated.sealedSigningSecrets(NOW)).containsExactly("ref-new", "ref-old");
		assertThat(rotated.sealedSigningSecrets(NOW.plus(Duration.ofHours(24)))).containsExactly("ref-new");
		assertThat(WebhookEndpoint.of(509, URL, Set.of(), "ref-1").sealedSigningSecrets(NOW)).containsExactly("ref-1");
	}

	@Test
	void pingIsNotSubscribedTo() {
		assertThatThrownBy(() -> WebhookEndpoint.of(509, URL, Set.of(WebhookEvent.PING), "ref-1"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void eventsGoByTheirWireNames() {
		assertThat(WebhookEvent.fromWireName("job.cancelled")).isEqualTo(WebhookEvent.JOB_CANCELLED);
		assertThatThrownBy(() -> WebhookEvent.fromWireName("job.unknown")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void deliveryIdsAreDlvAndSixteenLettersOrDigits() {
		assertThat(DeliveryIds.next()).matches("dlv_[0-9A-Za-z]{16}");
		assertThat(DeliveryIds.next()).isNotEqualTo(DeliveryIds.next());
		assertThat(DeliveryIds.next(RandomGenerator.of("L64X128MixRandom")))
				.matches("dlv_[0-9A-Za-z]{16}");
	}
}
