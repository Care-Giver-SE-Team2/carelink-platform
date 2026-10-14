package sg.nus.carelink.openplatform.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import sg.nus.carelink.openplatform.domain.model.Delivery;
import sg.nus.carelink.openplatform.domain.model.DeliveryAttempt;

/** The deliveries CareLink makes to providers' webhook endpoints, and every attempt at them. */
public interface WebhookDeliveries {

	/**
	 * Keeps a new delivery, unless one with the same {@code dedupeKey} is kept already; then that
	 * one is returned and nothing is written. A null key never matches.
	 */
	Delivery create(Delivery delivery, String dedupeKey);

	Optional<Delivery> find(String id);

	/** PENDING deliveries whose next attempt is due at {@code now}, longest due first. */
	List<Delivery> due(Instant now, int limit);

	/**
	 * Keeps {@code attempt} and moves the delivery from {@code before} to {@code after}, together,
	 * only if the delivery is still as {@code before} left it.
	 *
	 * @return false when the delivery had changed meanwhile, and nothing was written
	 */
	boolean recordAttempt(Delivery before, DeliveryAttempt attempt, Delivery after);

	/** Moves the delivery from {@code before} to {@code after}, without an attempt, if it is still as before. */
	boolean update(Delivery before, Delivery after);

	/** Every attempt at the delivery, the first first. */
	List<DeliveryAttempt> attempts(String deliveryId);
}
