package sg.nus.carelink.openplatform.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.openplatform.domain.model.Delivery;
import sg.nus.carelink.openplatform.domain.model.DeliveryAttempt;
import sg.nus.carelink.openplatform.domain.model.DeliveryIds;
import sg.nus.carelink.openplatform.domain.model.WebhookEndpoint;
import sg.nus.carelink.openplatform.domain.model.WebhookEvent;
import sg.nus.carelink.openplatform.domain.model.WebhookSignature;
import sg.nus.carelink.openplatform.domain.repository.WebhookDeliveries;
import sg.nus.carelink.openplatform.domain.repository.WebhookEndpoints;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * CareLink's webhooks to providers (docs/api/webhooks.md): making a delivery when something
 * happens to a provider's job, and attempting it until the endpoint answers 2xx or the retry
 * schedule runs out.
 *
 * <p>A delivery is created in the transaction of what caused it, with a key naming that cause, so
 * the same cause handled twice makes one delivery. Its attempts are made later, by the scheduler
 * calling {@link #attemptDue}, each outside any transaction: an endpoint taking ten seconds to
 * answer holds no database connection. Each attempt is signed afresh, with the time it is made,
 * over the body the delivery keeps.
 */
@Service
public class WebhookDeliveryService {

	private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

	/** How many due deliveries one run attempts; the rest wait for the next run. */
	static final int BATCH_SIZE = 20;

	static final String USER_AGENT = "CareLink-Webhooks/1";

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

	private static final DateTimeFormatter BODY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

	private final WebhookEndpoints endpoints;
	private final WebhookDeliveries deliveries;
	private final WebhookTransport transport;
	private final SigningSecrets secrets;
	private final JsonMapper json;
	private final Clock clock;

	public WebhookDeliveryService(WebhookEndpoints endpoints, WebhookDeliveries deliveries, WebhookTransport transport,
			SigningSecrets secrets, JsonMapper json, Clock clock) {
		this.endpoints = endpoints;
		this.deliveries = deliveries;
		this.transport = transport;
		this.secrets = secrets;
		this.json = json;
		this.clock = clock;
	}

	/**
	 * A delivery of {@code event} to the provider, due at once, unless the provider does not
	 * receive it: it has no endpoint, did not subscribe to the event, or is suspended.
	 *
	 * @param data the body's {@code data}, e.g. {@code {"job": ...}}
	 * @param dedupeKey what caused it, e.g. the id of the event handled; the delivery already made
	 *        for the same key is returned instead of a new one. Null for no deduplication
	 */
	@Transactional
	public Optional<Delivery> enqueue(long providerId, WebhookEvent event, String jobId, Object data,
			String dedupeKey) {
		Optional<WebhookEndpoint> endpoint = endpoints.find(providerId).filter(found -> found.receives(event));
		if (endpoint.isEmpty()) {
			return Optional.empty();
		}
		Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
		String id = DeliveryIds.next();
		String body = body(id, event, now, data);
		Delivery delivery = Delivery.created(id, providerId, event, jobId, endpoint.get().url(), body, now);
		return Optional.of(deliveries.create(delivery, dedupeKey));
	}

	/** The test event, sent whatever the provider subscribed to. */
	@Transactional
	public Delivery sendPing(long providerId) {
		return enqueue(providerId, WebhookEvent.PING, null, Map.of(), null)
				.orElseThrow(() -> new ResourceNotFound("Webhook endpoint of provider", providerId));
	}

	/** One more attempt at a failed delivery of the provider's, with the same id and body. */
	@Transactional
	public Delivery resend(long providerId, String deliveryId) {
		Delivery delivery = deliveries.find(deliveryId)
				.filter(found -> found.providerId() == providerId)
				.orElseThrow(() -> new ResourceNotFound("Webhook delivery", deliveryId));
		Delivery resent = delivery.resent(clock.instant());
		if (!deliveries.update(delivery, resent)) {
			throw new IllegalStateException("Delivery " + deliveryId + " changed while it was resent");
		}
		return resent;
	}

	/**
	 * Attempts the deliveries that are due, up to {@link #BATCH_SIZE}. A delivery that fails in
	 * CareLink's own code is left due, and the others are still attempted.
	 *
	 * @return how many were attempted
	 */
	public int attemptDue() {
		int attempted = 0;
		for (Delivery delivery : deliveries.due(clock.instant(), BATCH_SIZE)) {
			try {
				attempt(delivery);
				attempted++;
			}
			catch (RuntimeException failure) {
				log.warn("Webhook delivery {} not attempted: {}", delivery.id(), failure.getClass().getSimpleName());
			}
		}
		return attempted;
	}

	private void attempt(Delivery delivery) {
		Optional<WebhookEndpoint> endpoint = endpoints.find(delivery.providerId()).filter(WebhookEndpoint::enabled);
		if (endpoint.isEmpty()) {
			// Suspended, or its endpoint removed: nothing more is sent (webhooks.md, section 7)
			deliveries.update(delivery, delivery.abandoned());
			return;
		}
		Instant at = clock.instant();
		long timestamp = at.getEpochSecond();
		List<String> keys = endpoint.get().sealedSigningSecrets(at).stream().map(secrets::reveal).toList();
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("User-Agent", USER_AGENT);
		headers.put("X-CareLink-Delivery", delivery.id());
		headers.put("X-CareLink-Event", delivery.event().wireName());
		headers.put("X-CareLink-Timestamp", Long.toString(timestamp));
		headers.put("X-CareLink-Signature", WebhookSignature.header(keys, timestamp, delivery.body()));

		WebhookTransport.Outcome outcome = transport.post(delivery.url(), headers, delivery.body());
		DeliveryAttempt attempt = new DeliveryAttempt(delivery.id(), delivery.nextAttemptNo(), at, outcome.status(),
				outcome.durationMs(), outcome.error());
		Delivery after = delivery.after(attempt);
		if (!deliveries.recordAttempt(delivery, attempt, after)) {
			log.warn("Webhook delivery {} changed during attempt {}; the attempt is not kept", delivery.id(),
					attempt.attemptNo());
		}
		else if (after.status() == Delivery.Status.FAILED) {
			log.info("Webhook delivery {} failed after {} attempts", delivery.id(), after.attemptCount());
		}
	}

	/** The envelope every event has (webhooks.md, section 4), in that order. */
	private String body(String id, WebhookEvent event, Instant createdAt, Object data) {
		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("id", id);
		envelope.put("type", event.wireName());
		envelope.put("createdAt", BODY_TIME.format(createdAt.truncatedTo(ChronoUnit.SECONDS).atZone(SINGAPORE)));
		envelope.put("data", data);
		return json.writeValueAsString(envelope);
	}
}
