package sg.nus.carelink.openplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import sg.nus.carelink.openplatform.application.SigningSecrets;
import sg.nus.carelink.openplatform.application.WebhookDeliveryService;
import sg.nus.carelink.openplatform.domain.model.Delivery;
import sg.nus.carelink.openplatform.domain.model.DeliveryAttempt;
import sg.nus.carelink.openplatform.domain.model.WebhookEndpoint;
import sg.nus.carelink.openplatform.domain.model.WebhookEvent;
import sg.nus.carelink.openplatform.domain.model.WebhookSignature;
import sg.nus.carelink.openplatform.domain.repository.WebhookDeliveries;
import sg.nus.carelink.openplatform.domain.repository.WebhookEndpoints;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * Webhook delivery against notification's own database, built by its migrations, and a real HTTP
 * endpoint on this machine standing in for a provider's. The endpoint verifies each request the way
 * docs/api/webhooks.md tells providers to. Signing secrets are sealed with the tests' key, as a real
 * run seals them. The scheduler is off: the test runs the attempts itself, on a clock it moves.
 */
@SpringBootTest(properties = {
		"carelink.webhooks.scheduler-enabled=false",
		"carelink.webhooks.allow-local-endpoints=true",
		"carelink.webhooks.request-timeout=2s" })
class WebhookDeliveryIT {

	private static final Instant NOW = Instant.parse("2026-10-15T01:02:11.123Z"); // 09:02:11 in Singapore

	private static final String SECRET = "whsec_it_secret";

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, WebhookDeliveryIT.class, null, "connectionTimeZone=Asia/Singapore");
	}

	@TestConfiguration
	static class Fixtures {

		@Bean
		@Primary
		MovableClock movableClock() {
			return new MovableClock();
		}
	}

	@Autowired
	private WebhookDeliveryService service;
	@Autowired
	private WebhookEndpoints endpoints;
	@Autowired
	private WebhookDeliveries deliveries;
	@Autowired
	private SigningSecrets secrets;
	@Autowired
	private MovableClock clock;
	@Autowired
	private JdbcTemplate jdbc;

	private HttpServer provider;
	private final List<String> verified = new CopyOnWriteArrayList<>();
	private final AtomicInteger answer = new AtomicInteger(204);
	private final long providerId = 509;

	@BeforeEach
	void provider() throws IOException {
		clock.set(NOW);
		jdbc.update("DELETE FROM webhook_delivery_attempt");
		jdbc.update("DELETE FROM webhook_delivery");
		jdbc.update("DELETE FROM provider_webhook_endpoint");
		provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		provider.createContext("/carelink", exchange -> {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String timestamp = exchange.getRequestHeaders().getFirst("X-CareLink-Timestamp");
			String signature = exchange.getRequestHeaders().getFirst("X-CareLink-Signature");
			// What the spec tells a provider to check, with the secret it was given
			if (("v1=" + WebhookSignature.sign(SECRET, Long.parseLong(timestamp), body)).equals(signature)) {
				verified.add(exchange.getRequestHeaders().getFirst("X-CareLink-Delivery"));
			}
			exchange.sendResponseHeaders(answer.get(), -1);
			exchange.close();
		});
		provider.start();
		endpoints.save(WebhookEndpoint.of(providerId,
				URI.create("http://127.0.0.1:" + provider.getAddress().getPort() + "/carelink"),
				Set.of(WebhookEvent.JOB_DISPATCHED, WebhookEvent.JOB_CANCELLED), secrets.store(providerId, SECRET)));
	}

	@AfterEach
	void stop() {
		provider.stop(0);
	}

	@Test
	void aDeliveryReachesTheProviderSignedSoItVerifiesAndIsKeptAsDelivered() {
		Delivery delivery = enqueue("evt-delivered");

		assertThat(service.attemptDue()).isEqualTo(1);

		assertThat(verified).containsExactly(delivery.id());
		assertThat(deliveries.find(delivery.id()).orElseThrow().status()).isEqualTo(Delivery.Status.DELIVERED);
		assertThat(deliveries.attempts(delivery.id())).singleElement().satisfies(attempt -> {
			assertThat(attempt.attemptNo()).isEqualTo(1);
			assertThat(attempt.responseStatus()).isEqualTo(204);
			assertThat(attempt.at()).isEqualTo(NOW);
		});
	}

	@Test
	void theDatabaseKeepsTheSigningSecretEncryptedNeverAsItself() {
		String kept = jdbc.queryForObject("SELECT signing_secret FROM provider_webhook_endpoint WHERE provider_id = ?",
				String.class, providerId);

		assertThat(kept).startsWith("v1:").doesNotContain(SECRET);
		assertThat(secrets.reveal(kept)).isEqualTo(SECRET);
	}

	@Test
	void theSameCauseMakesOneRow() {
		Delivery first = enqueue("evt-twice");
		Delivery again = enqueue("evt-twice");
		Delivery raced = deliveries.create(Delivery.created("dlv_racedracedraced", providerId,
				WebhookEvent.JOB_DISPATCHED, "JOB-1", first.url(), "{}", NOW), "evt-twice");

		assertThat(again.id()).isEqualTo(first.id());
		assertThat(raced.id()).isEqualTo(first.id());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM webhook_delivery WHERE dedupe_key = 'evt-twice'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void aFailedAttemptIsKeptAndTheNextIsDueOnScheduleInSingaporeTime() {
		answer.set(500);
		Delivery delivery = enqueue("evt-retried");

		service.attemptDue();

		Delivery after = deliveries.find(delivery.id()).orElseThrow();
		assertThat(after.status()).isEqualTo(Delivery.Status.PENDING);
		assertThat(after.attemptCount()).isEqualTo(1);
		assertThat(after.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
		// The column keeps the wall-clock time in Singapore, as every time in notification's database
		assertThat(jdbc.queryForObject("SELECT DATE_FORMAT(next_attempt_at, '%Y-%m-%d %H:%i:%s') FROM webhook_delivery"
				+ " WHERE id = ?", String.class, delivery.id())).isEqualTo("2026-10-15 09:03:11");

		answer.set(200);
		assertThat(service.attemptDue()).as("not yet due").isZero();
		clock.set(NOW.plus(Duration.ofMinutes(1)));
		service.attemptDue();

		assertThat(deliveries.find(delivery.id()).orElseThrow().status()).isEqualTo(Delivery.Status.DELIVERED);
		assertThat(deliveries.attempts(delivery.id())).extracting(DeliveryAttempt::responseStatus)
				.containsExactly(500, 200);
		assertThat(verified).containsExactly(delivery.id(), delivery.id());
	}

	@Test
	void aWriterHoldingAnOldCopyChangesNothing() {
		Delivery delivery = enqueue("evt-stale");
		DeliveryAttempt first = new DeliveryAttempt(delivery.id(), 1, NOW, 503, 10, null);
		assertThat(deliveries.recordAttempt(delivery, first, delivery.after(first))).isTrue();

		DeliveryAttempt sameAgain = new DeliveryAttempt(delivery.id(), 1, NOW, 200, 10, null);

		assertThat(deliveries.recordAttempt(delivery, sameAgain, delivery.after(sameAgain))).isFalse();
		assertThat(deliveries.find(delivery.id()).orElseThrow().status()).isEqualTo(Delivery.Status.PENDING);
		assertThat(deliveries.attempts(delivery.id())).extracting(DeliveryAttempt::responseStatus).containsExactly(503);
	}

	@Test
	void anEndpointKeepsItsEventsSecretsAndSuspension() {
		Instant until = NOW.plus(Duration.ofHours(24));
		endpoints.save(new WebhookEndpoint(providerId, URI.create("https://hooks.example.sg/new"),
				Set.of(WebhookEvent.JOB_CANCELLED), "ref-new", "ref-old", until, false));

		WebhookEndpoint kept = endpoints.find(providerId).orElseThrow();

		assertThat(kept.url()).isEqualTo(URI.create("https://hooks.example.sg/new"));
		assertThat(kept.events()).containsExactly(WebhookEvent.JOB_CANCELLED);
		assertThat(kept.sealedSigningSecrets(NOW)).containsExactly("ref-new", "ref-old");
		assertThat(kept.previousSecretUntil()).isEqualTo(until);
		assertThat(kept.enabled()).isFalse();
	}

	private Delivery enqueue(String cause) {
		return service.enqueue(providerId, WebhookEvent.JOB_DISPATCHED, "JOB-24081",
				Map.of("job", Map.of("id", "JOB-24081", "status", "NEW")), cause).orElseThrow();
	}

	/** The application's clock, moved by the test. */
	static final class MovableClock extends Clock {

		private volatile Instant now = NOW;

		void set(Instant instant) {
			now = instant;
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("Asia/Singapore");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}
}
