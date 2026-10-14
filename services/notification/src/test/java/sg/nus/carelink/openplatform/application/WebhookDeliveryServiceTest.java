package sg.nus.carelink.openplatform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.openplatform.domain.model.Delivery;
import sg.nus.carelink.openplatform.domain.model.WebhookEndpoint;
import sg.nus.carelink.openplatform.domain.model.WebhookEvent;
import sg.nus.carelink.openplatform.domain.model.WebhookSignature;
import sg.nus.carelink.openplatform.domain.repository.WebhookEndpoints;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * The delivery rules end to end, with the endpoint and the store in memory: who is sent what, one
 * delivery per cause, each attempt signed afresh over the same body, the retry schedule from
 * failure to giving up, and nothing more for a suspended provider.
 */
class WebhookDeliveryServiceTest {

	private static final long PROVIDER = 509;

	private static final URI URL = URI.create("https://hooks.example.sg/carelink");

	private static final Instant NOW = Instant.parse("2026-10-15T01:02:11.123Z"); // 09:02:11 in Singapore

	private static final Map<String, Object> JOB = Map.of("job", Map.of("id", "JOB-24081", "status", "NEW"));

	private final MutableClock clock = new MutableClock(NOW);
	private final Map<Long, WebhookEndpoint> endpointRows = new HashMap<>();
	private final InMemoryWebhookDeliveries deliveries = new InMemoryWebhookDeliveries();
	private final RecordingTransport transport = new RecordingTransport();
	private final Map<String, String> secretValues = new HashMap<>(Map.of("ref-1", "whsec_one", "ref-0", "whsec_zero"));
	private final JsonMapper json = JsonMapper.builder().build();

	private final WebhookDeliveryService service = new WebhookDeliveryService(new WebhookEndpoints() {
		@Override
		public Optional<WebhookEndpoint> find(long providerId) {
			return Optional.ofNullable(endpointRows.get(providerId));
		}

		@Override
		public void save(WebhookEndpoint endpoint) {
			endpointRows.put(endpoint.providerId(), endpoint);
		}
	}, deliveries, transport, new SigningSecrets() {
		@Override
		public String store(long providerId, String secret) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String reveal(String ref) {
			return secretValues.get(ref);
		}
	}, json, clock);

	private void subscribed(WebhookEvent... events) {
		endpointRows.put(PROVIDER, WebhookEndpoint.of(PROVIDER, URL, Set.of(events), "ref-1"));
	}

	@Test
	void aSubscribedEventBecomesADeliveryDueAtOnceInTheSpecsEnvelope() {
		subscribed(WebhookEvent.JOB_DISPATCHED);

		Delivery delivery = service.enqueue(PROVIDER, WebhookEvent.JOB_DISPATCHED, "JOB-24081", JOB, "evt-1").orElseThrow();

		assertThat(delivery.id()).matches("dlv_[0-9A-Za-z]{16}");
		assertThat(delivery.status()).isEqualTo(Delivery.Status.PENDING);
		assertThat(delivery.url()).isEqualTo(URL);
		JsonNode body = json.readTree(delivery.body());
		assertThat(body.propertyNames()).containsExactly("id", "type", "createdAt", "data");
		assertThat(body.get("id").asString()).isEqualTo(delivery.id());
		assertThat(body.get("type").asString()).isEqualTo("job.dispatched");
		assertThat(body.get("createdAt").asString()).isEqualTo("2026-10-15T09:02:11+08:00");
		assertThat(body.at("/data/job/id").asString()).isEqualTo("JOB-24081");
	}

	@Test
	void nothingIsMadeForAnEventNotSubscribedToNoEndpointOrASuspendedProvider() {
		assertThat(service.enqueue(PROVIDER, WebhookEvent.JOB_DISPATCHED, "JOB-1", JOB, null)).isEmpty();

		subscribed(WebhookEvent.JOB_DISPATCHED);
		assertThat(service.enqueue(PROVIDER, WebhookEvent.JOB_CANCELLED, "JOB-1", JOB, null)).isEmpty();

		endpointRows.put(PROVIDER, new WebhookEndpoint(PROVIDER, URL, Set.of(WebhookEvent.JOB_DISPATCHED), "ref-1",
				null, null, false));
		assertThat(service.enqueue(PROVIDER, WebhookEvent.JOB_DISPATCHED, "JOB-1", JOB, null)).isEmpty();
		assertThat(deliveries.byId).isEmpty();
	}

	@Test
	void theSameCauseHandledTwiceMakesOneDelivery() {
		subscribed(WebhookEvent.JOB_DISPATCHED);

		Delivery first = service.enqueue(PROVIDER, WebhookEvent.JOB_DISPATCHED, "JOB-1", JOB, "evt-1").orElseThrow();
		Delivery again = service.enqueue(PROVIDER, WebhookEvent.JOB_DISPATCHED, "JOB-1", JOB, "evt-1").orElseThrow();

		assertThat(again).isEqualTo(first);
		assertThat(deliveries.byId).hasSize(1);
	}

	@Test
	void anAttemptSendsTheBodySignedWithTheTimeItIsMade() {
		subscribed(WebhookEvent.JOB_DISPATCHED);
		Delivery delivery = service.enqueue(PROVIDER, WebhookEvent.JOB_DISPATCHED, "JOB-1", JOB, null).orElseThrow();
		transport.answers(204);

		assertThat(service.attemptDue()).isEqualTo(1);

		RecordingTransport.Request sent = transport.sent.getFirst();
		long timestamp = NOW.getEpochSecond();
		assertThat(sent.url()).isEqualTo(URL);
		assertThat(sent.body()).isEqualTo(delivery.body());
		assertThat(sent.headers()).containsEntry("Content-Type", "application/json")
				.containsEntry("User-Agent", "CareLink-Webhooks/1")
				.containsEntry("X-CareLink-Delivery", delivery.id())
				.containsEntry("X-CareLink-Event", "job.dispatched")
				.containsEntry("X-CareLink-Timestamp", Long.toString(timestamp))
				.containsEntry("X-CareLink-Signature", "v1=" + WebhookSignature.sign("whsec_one", timestamp, delivery.body()));
		assertThat(deliveries.find(delivery.id()).orElseThrow().status()).isEqualTo(Delivery.Status.DELIVERED);
		assertThat(deliveries.attempts(delivery.id())).singleElement()
				.satisfies(attempt -> assertThat(attempt.responseStatus()).isEqualTo(204));
	}

	@Test
	void aFailedAttemptIsRetriedOnScheduleWithTheSameIdAndBodyAndAFreshSignature() {
		subscribed(WebhookEvent.JOB_DISPATCHED);
		Delivery delivery = service.enqueue(PROVIDER, WebhookEvent.JOB_DISPATCHED, "JOB-1", JOB, null).orElseThrow();
		transport.answers(500);
		transport.timesOut();
		transport.answers(200);

		service.attemptDue();
		assertThat(deliveries.find(delivery.id()).orElseThrow().nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
		assertThat(service.attemptDue()).as("not due again yet").isZero();

		clock.advance(Duration.ofMinutes(1));
		service.attemptDue();
		assertThat(deliveries.find(delivery.id()).orElseThrow().nextAttemptAt())
				.isEqualTo(NOW.plus(Duration.ofMinutes(6)));

		clock.advance(Duration.ofMinutes(5));
		service.attemptDue();

		assertThat(transport.sent).hasSize(3)
				.allSatisfy(sent -> {
					assertThat(sent.headers().get("X-CareLink-Delivery")).isEqualTo(delivery.id());
					assertThat(sent.body()).isEqualTo(delivery.body());
				});
		assertThat(transport.sent.stream().map(sent -> sent.headers().get("X-CareLink-Signature")).distinct())
				.as("each attempt is signed with its own timestamp").hasSize(3);
		assertThat(deliveries.find(delivery.id()).orElseThrow().status()).isEqualTo(Delivery.Status.DELIVERED);
		assertThat(deliveries.attempts(delivery.id()))
				.extracting(attempt -> attempt.responseStatus() == null ? attempt.error() : attempt.responseStatus())
				.containsExactly(500, "TIMEOUT", 200);
	}

	@Test
	void afterSixFailuresCareLinkGivesUpAndAResendTriesOnceMore() {
		subscribed(WebhookEvent.JOB_DISPATCHED);
		Delivery delivery = service.enqueue(PROVIDER, WebhookEvent.JOB_DISPATCHED, "JOB-1", JOB, null).orElseThrow();
		for (int i = 0; i < 6; i++) {
			transport.answers(503);
		}

		for (int attempt = 0; attempt < 6; attempt++) {
			service.attemptDue();
			clock.advance(Duration.ofHours(6));
		}

		assertThat(deliveries.find(delivery.id()).orElseThrow().status()).isEqualTo(Delivery.Status.FAILED);
		assertThat(transport.sent).hasSize(6);
		assertThat(service.attemptDue()).isZero();

		service.resend(PROVIDER, delivery.id());
		transport.answers(200);
		service.attemptDue();

		assertThat(transport.sent).hasSize(7);
		assertThat(transport.sent.getLast().headers().get("X-CareLink-Delivery")).isEqualTo(delivery.id());
		assertThat(deliveries.find(delivery.id()).orElseThrow().status()).isEqualTo(Delivery.Status.DELIVERED);
	}

	@Test
	void onlyTheProvidersOwnFailedDeliveryCanBeResent() {
		subscribed(WebhookEvent.JOB_DISPATCHED);
		Delivery delivery = service.enqueue(PROVIDER, WebhookEvent.JOB_DISPATCHED, "JOB-1", JOB, null).orElseThrow();

		assertThatThrownBy(() -> service.resend(PROVIDER, delivery.id())).isInstanceOf(BusinessRuleViolation.class);
		assertThatThrownBy(() -> service.resend(PROVIDER + 1, delivery.id())).isInstanceOf(ResourceNotFound.class);
		assertThatThrownBy(() -> service.resend(PROVIDER, "dlv_unknown")).isInstanceOf(ResourceNotFound.class);
	}

	@Test
	void aPingGoesWhateverTheSubscriptionAndNeedsAnEndpoint() {
		assertThatThrownBy(() -> service.sendPing(PROVIDER)).isInstanceOf(ResourceNotFound.class);
		subscribed();

		Delivery ping = service.sendPing(PROVIDER);

		assertThat(ping.event()).isEqualTo(WebhookEvent.PING);
		assertThat(ping.jobId()).isNull();
		assertThat(json.readTree(ping.body()).get("data").isEmpty()).isTrue();
	}

	@Test
	void duringARotationEachAttemptIsSignedWithBothSecrets() {
		endpointRows.put(PROVIDER, new WebhookEndpoint(PROVIDER, URL, Set.of(), "ref-1", "ref-0",
				NOW.plus(Duration.ofHours(24)), true));
		Delivery ping = service.sendPing(PROVIDER);
		transport.answers(200);

		service.attemptDue();

		long timestamp = NOW.getEpochSecond();
		assertThat(transport.sent.getFirst().headers().get("X-CareLink-Signature")).isEqualTo(
				"v1=" + WebhookSignature.sign("whsec_one", timestamp, ping.body())
						+ ",v1=" + WebhookSignature.sign("whsec_zero", timestamp, ping.body()));
	}

	@Test
	void aDeliveryToAProviderSuspendedSinceIsAbandonedWithoutAnAttempt() {
		subscribed(WebhookEvent.JOB_DISPATCHED);
		Delivery delivery = service.enqueue(PROVIDER, WebhookEvent.JOB_DISPATCHED, "JOB-1", JOB, null).orElseThrow();
		endpointRows.put(PROVIDER, new WebhookEndpoint(PROVIDER, URL, Set.of(WebhookEvent.JOB_DISPATCHED), "ref-1",
				null, null, false));

		service.attemptDue();

		assertThat(transport.sent).isEmpty();
		assertThat(deliveries.find(delivery.id()).orElseThrow().status()).isEqualTo(Delivery.Status.FAILED);
	}

	@Test
	void aDeliveryThatFailsInCareLinksOwnCodeIsLeftDueAndTheRestAreStillAttempted() {
		subscribed(WebhookEvent.JOB_DISPATCHED);
		Delivery broken = service.enqueue(PROVIDER, WebhookEvent.JOB_DISPATCHED, "JOB-1", JOB, null).orElseThrow();
		clock.advance(Duration.ofSeconds(1));
		Delivery fine = service.enqueue(PROVIDER, WebhookEvent.JOB_DISPATCHED, "JOB-2", JOB, null).orElseThrow();
		transport.failsFor(broken.id());
		transport.answers(200);

		assertThat(service.attemptDue()).isEqualTo(1);

		assertThat(deliveries.find(broken.id()).orElseThrow().status()).isEqualTo(Delivery.Status.PENDING);
		assertThat(deliveries.find(fine.id()).orElseThrow().status()).isEqualTo(Delivery.Status.DELIVERED);
	}

	/** Answers each request with the next outcome queued, and keeps what was sent. */
	static final class RecordingTransport implements WebhookTransport {

		record Request(URI url, Map<String, String> headers, String body) {
		}

		final List<Request> sent = new ArrayList<>();
		private final Deque<Outcome> outcomes = new ArrayDeque<>();
		private String failingDelivery;

		void answers(int status) {
			outcomes.add(Outcome.answered(status, 30));
		}

		void timesOut() {
			outcomes.add(Outcome.unanswered("TIMEOUT", 10_000));
		}

		void failsFor(String deliveryId) {
			failingDelivery = deliveryId;
		}

		@Override
		public Outcome post(URI url, Map<String, String> headers, String body) {
			if (headers.get("X-CareLink-Delivery").equals(failingDelivery)) {
				throw new IllegalStateException("a bug of CareLink's");
			}
			sent.add(new Request(url, Map.copyOf(headers), body));
			return outcomes.removeFirst();
		}
	}

	static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}
}
