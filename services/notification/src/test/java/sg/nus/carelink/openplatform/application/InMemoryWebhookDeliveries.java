package sg.nus.carelink.openplatform.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import sg.nus.carelink.openplatform.domain.model.Delivery;
import sg.nus.carelink.openplatform.domain.model.DeliveryAttempt;
import sg.nus.carelink.openplatform.domain.repository.WebhookDeliveries;

/** The deliveries in memory, with the same rules as the JDBC adapter: one per key, changes only from the state read. */
class InMemoryWebhookDeliveries implements WebhookDeliveries {

	final Map<String, Delivery> byId = new LinkedHashMap<>();
	final Map<String, String> idByKey = new HashMap<>();
	final List<DeliveryAttempt> attempts = new ArrayList<>();

	@Override
	public Delivery create(Delivery delivery, String dedupeKey) {
		if (dedupeKey != null && idByKey.containsKey(dedupeKey)) {
			return byId.get(idByKey.get(dedupeKey));
		}
		byId.put(delivery.id(), delivery);
		if (dedupeKey != null) {
			idByKey.put(dedupeKey, delivery.id());
		}
		return delivery;
	}

	@Override
	public Optional<Delivery> find(String id) {
		return Optional.ofNullable(byId.get(id));
	}

	@Override
	public List<Delivery> due(Instant now, int limit) {
		return byId.values().stream()
				.filter(delivery -> delivery.status() == Delivery.Status.PENDING && !delivery.nextAttemptAt().isAfter(now))
				.sorted(Comparator.comparing(Delivery::nextAttemptAt))
				.limit(limit)
				.toList();
	}

	@Override
	public boolean recordAttempt(Delivery before, DeliveryAttempt attempt, Delivery after) {
		if (!update(before, after)) {
			return false;
		}
		attempts.add(attempt);
		return true;
	}

	@Override
	public boolean update(Delivery before, Delivery after) {
		if (!before.equals(byId.get(before.id()))) {
			return false;
		}
		byId.put(after.id(), after);
		return true;
	}

	@Override
	public List<DeliveryAttempt> attempts(String deliveryId) {
		return attempts.stream().filter(attempt -> attempt.deliveryId().equals(deliveryId)).toList();
	}
}
