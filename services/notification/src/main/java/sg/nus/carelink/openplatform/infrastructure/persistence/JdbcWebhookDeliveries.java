package sg.nus.carelink.openplatform.infrastructure.persistence;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.openplatform.domain.model.Delivery;
import sg.nus.carelink.openplatform.domain.model.DeliveryAttempt;
import sg.nus.carelink.openplatform.domain.model.WebhookEvent;
import sg.nus.carelink.openplatform.domain.repository.WebhookDeliveries;

/**
 * {@code webhook_delivery} and {@code webhook_delivery_attempt}. Every change to a delivery is a
 * conditional update on the state it was read in, so two writers cannot both move it on: the
 * second changes nothing and is told so.
 */
@Repository
class JdbcWebhookDeliveries implements WebhookDeliveries {

	private static final String COLUMNS = """
			id, provider_id, event_type, job_id, url, body, status, attempt_count, max_attempts, next_attempt_at,
			created_at""";

	private final JdbcTemplate jdbc;
	private final Clock clock;

	JdbcWebhookDeliveries(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public Delivery create(Delivery delivery, String dedupeKey) {
		if (dedupeKey != null) {
			Optional<Delivery> kept = byDedupeKey(dedupeKey);
			if (kept.isPresent()) {
				return kept.get();
			}
		}
		try {
			jdbc.update("""
					INSERT INTO webhook_delivery
					    (id, provider_id, event_type, job_id, dedupe_key, url, body, status, attempt_count, max_attempts,
					     next_attempt_at, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
					delivery.id(), delivery.providerId(), delivery.event().wireName(), delivery.jobId(), dedupeKey,
					delivery.url().toString(), delivery.body(), delivery.status().name(), delivery.attemptCount(),
					delivery.maxAttempts(), timestamp(delivery.nextAttemptAt()), timestamp(delivery.createdAt()),
					timestamp(delivery.createdAt()));
			return delivery;
		}
		catch (DuplicateKeyException race) {
			// Another handler of the same cause got there between the read and the insert
			if (dedupeKey == null) {
				throw race;
			}
			return byDedupeKey(dedupeKey).orElseThrow(() -> race);
		}
	}

	@Override
	public Optional<Delivery> find(String id) {
		return jdbc.query("SELECT " + COLUMNS + " FROM webhook_delivery WHERE id = ?", JdbcWebhookDeliveries::delivery,
				id).stream().findFirst();
	}

	@Override
	public List<Delivery> due(Instant now, int limit) {
		return jdbc.query("SELECT " + COLUMNS + """
				 FROM webhook_delivery
				WHERE status = 'PENDING' AND next_attempt_at <= ?
				ORDER BY next_attempt_at
				LIMIT ?""", JdbcWebhookDeliveries::delivery, Timestamp.from(now), limit);
	}

	@Override
	@Transactional
	public boolean recordAttempt(Delivery before, DeliveryAttempt attempt, Delivery after) {
		if (!update(before, after)) {
			return false;
		}
		jdbc.update("""
				INSERT INTO webhook_delivery_attempt
				    (delivery_id, attempt_no, attempted_at, response_status, duration_ms, error)
				VALUES (?, ?, ?, ?, ?, ?)""",
				attempt.deliveryId(), attempt.attemptNo(), Timestamp.from(attempt.at()), attempt.responseStatus(),
				Math.toIntExact(Math.min(attempt.durationMs(), Integer.MAX_VALUE)), attempt.error());
		return true;
	}

	@Override
	public boolean update(Delivery before, Delivery after) {
		if (!before.id().equals(after.id())) {
			throw new IllegalArgumentException("before and after are different deliveries");
		}
		return jdbc.update("""
				UPDATE webhook_delivery
				SET status = ?, attempt_count = ?, max_attempts = ?, next_attempt_at = ?, updated_at = ?
				WHERE id = ? AND status = ? AND attempt_count = ? AND max_attempts = ?""",
				after.status().name(), after.attemptCount(), after.maxAttempts(), timestamp(after.nextAttemptAt()),
				Timestamp.from(clock.instant()),
				before.id(), before.status().name(), before.attemptCount(), before.maxAttempts()) == 1;
	}

	@Override
	public List<DeliveryAttempt> attempts(String deliveryId) {
		return jdbc.query("""
				SELECT delivery_id, attempt_no, attempted_at, response_status, duration_ms, error
				FROM webhook_delivery_attempt WHERE delivery_id = ? ORDER BY attempt_no""",
				(row, rowNum) -> new DeliveryAttempt(
						row.getString("delivery_id"),
						row.getInt("attempt_no"),
						row.getTimestamp("attempted_at").toInstant(),
						row.getObject("response_status", Integer.class),
						row.getLong("duration_ms"),
						row.getString("error")),
				deliveryId);
	}

	private Optional<Delivery> byDedupeKey(String dedupeKey) {
		return jdbc.query("SELECT " + COLUMNS + " FROM webhook_delivery WHERE dedupe_key = ?",
				JdbcWebhookDeliveries::delivery, dedupeKey).stream().findFirst();
	}

	private static Delivery delivery(ResultSet row, int rowNum) throws SQLException {
		Timestamp next = row.getTimestamp("next_attempt_at");
		return new Delivery(
				row.getString("id"),
				row.getLong("provider_id"),
				WebhookEvent.fromWireName(row.getString("event_type")),
				row.getString("job_id"),
				URI.create(row.getString("url")),
				row.getString("body"),
				Delivery.Status.valueOf(row.getString("status")),
				row.getInt("attempt_count"),
				row.getInt("max_attempts"),
				next == null ? null : next.toInstant(),
				row.getTimestamp("created_at").toInstant());
	}

	private static Timestamp timestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}
}
