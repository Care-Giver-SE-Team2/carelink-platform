package sg.nus.carelink.events;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/** Writes events to {@code outbox_event} through the caller's transaction. */
final class JdbcEvents implements Events {

	private static final String INSERT = """
			INSERT INTO outbox_event (event_id, source, type, payload, occurred_at)
			VALUES (?, ?, ?, ?, ?)
			""";

	private final JdbcTemplate jdbc;

	private final JsonMapper json;

	private final String service;

	private final Clock clock;

	JdbcEvents(JdbcTemplate jdbc, JsonMapper json, String service, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.service = service;
		this.clock = clock;
	}

	@Override
	public void publish(String type, Object payload) {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(payload, "payload");
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalTransactionStateException(
					"An event is published in the transaction that changes the data it is about");
		}
		jdbc.update(INSERT, UUID.randomUUID().toString(), service, type, json.writeValueAsString(payload),
				Timestamp.from(clock.instant()));
	}

}
