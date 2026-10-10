package sg.nus.carelink.events;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes this service's outbox to the SNS topic.
 *
 * <p>Every replica runs a relay. Each takes a batch of unpublished events with
 * {@code SELECT ... FOR UPDATE SKIP LOCKED}, so two replicas never publish the same event at the
 * same time, and marks them published in the same transaction. A replica that stops between
 * publishing and committing leaves its batch to be published again, and the receivers ignore the
 * second copy ({@code consumed_message}).
 *
 * <p>A batch goes out in the order its events were written. When SNS refuses one, the relay
 * records the error on it and ends the batch, so the events after it wait instead of overtaking it.
 */
final class OutboxRelay implements SmartLifecycle, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	private static final String PENDING = """
			SELECT id, event_id, type, payload, occurred_at
			FROM outbox_event
			WHERE source = ? AND published_at IS NULL
			ORDER BY id
			LIMIT ?
			FOR UPDATE SKIP LOCKED
			""";

	private static final String PUBLISHED =
			"UPDATE outbox_event SET published_at = ?, attempts = attempts + 1, last_error = NULL WHERE id = ?";

	private static final String REFUSED = "UPDATE outbox_event SET attempts = attempts + 1, last_error = ? WHERE id = ?";

	private static final int ERROR_LENGTH = 500;

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transactions;

	private final SnsClient sns;

	private final JsonMapper json;

	private final Clock clock;

	private final String service;

	private final String topicArn;

	private final int batchSize;

	private final Duration interval;

	private ScheduledExecutorService executor;

	private volatile boolean running;

	OutboxRelay(JdbcTemplate jdbc, TransactionTemplate transactions, SnsClient sns, JsonMapper json, Clock clock,
			String service, EventsProperties properties) {
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.sns = sns;
		this.json = json;
		this.clock = clock;
		this.service = service;
		this.topicArn = properties.topicArn();
		this.batchSize = properties.relayBatchSize();
		this.interval = properties.relayInterval();
	}

	@Override
	public void start() {
		executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("outbox-relay").daemon(true).factory());
		running = true;
		executor.scheduleWithFixedDelay(this::drain, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
	}

	@Override
	public void stop() {
		running = false;
		if (executor == null) {
			return;
		}
		executor.shutdown();
		try {
			executor.awaitTermination(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public void destroy() {
		sns.close();
	}

	/** Publishes batches until the outbox is empty or SNS refuses an event. */
	void drain() {
		try {
			int published;
			do {
				published = relayBatch();
			}
			while (running && published == batchSize);
		}
		catch (RuntimeException failed) {
			log.warn("Relaying the outbox failed; trying again in {}", interval, failed);
		}
	}

	/** Publishes one batch in one transaction, and answers how many events went out. */
	int relayBatch() {
		Integer published = transactions.execute(status -> {
			List<Pending> batch = jdbc.query(PENDING, OutboxRelay::pending, service, batchSize);
			int count = 0;
			for (Pending event : batch) {
				try {
					sns.publish(request(event));
				}
				catch (SdkException refused) {
					jdbc.update(REFUSED, abbreviate(refused.getMessage()), event.id());
					log.warn("SNS refused {} event {}; it and the events after it are tried again in {}",
							event.type(), event.eventId(), interval, refused);
					break;
				}
				jdbc.update(PUBLISHED, Timestamp.from(clock.instant()), event.id());
				count++;
			}
			return count;
		});
		return published == null ? 0 : published;
	}

	private PublishRequest request(Pending event) {
		Envelope envelope = new Envelope(event.eventId(), event.type(), service, event.occurredAt(),
				json.readTree(event.payload()));
		return PublishRequest.builder()
				.topicArn(topicArn)
				.message(envelope.toJson(json))
				// For the subscriptions' filter policies
				.messageAttributes(Map.of("type", attribute(event.type()), "source", attribute(service)))
				.build();
	}

	private static MessageAttributeValue attribute(String value) {
		return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
	}

	private static String abbreviate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() <= ERROR_LENGTH ? message : message.substring(0, ERROR_LENGTH);
	}

	private static Pending pending(ResultSet row, int number) throws SQLException {
		return new Pending(row.getLong("id"), row.getString("event_id"), row.getString("type"),
				row.getString("payload"), row.getTimestamp("occurred_at").toInstant());
	}

	private record Pending(long id, String eventId, String type, String payload, Instant occurredAt) {
	}

}
