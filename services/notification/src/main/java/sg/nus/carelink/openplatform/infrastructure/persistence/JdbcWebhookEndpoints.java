package sg.nus.carelink.openplatform.infrastructure.persistence;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import sg.nus.carelink.openplatform.domain.model.WebhookEndpoint;
import sg.nus.carelink.openplatform.domain.model.WebhookEvent;
import sg.nus.carelink.openplatform.domain.repository.WebhookEndpoints;

/**
 * {@code provider_webhook_endpoint}. Times are bound as JDBC Timestamps; the connection's
 * Singapore time zone turns them into the wall-clock times the columns keep.
 */
@Repository
class JdbcWebhookEndpoints implements WebhookEndpoints {

	private final JdbcTemplate jdbc;
	private final Clock clock;

	JdbcWebhookEndpoints(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public Optional<WebhookEndpoint> find(long providerId) {
		return jdbc.query("""
				SELECT provider_id, url, events, signing_secret, previous_signing_secret, previous_secret_until, enabled
				FROM provider_webhook_endpoint WHERE provider_id = ?""", JdbcWebhookEndpoints::endpoint, providerId)
				.stream().findFirst();
	}

	@Override
	public void save(WebhookEndpoint endpoint) {
		Timestamp now = Timestamp.from(clock.instant());
		jdbc.update("""
				INSERT INTO provider_webhook_endpoint
				    (provider_id, url, events, signing_secret, previous_signing_secret, previous_secret_until, enabled,
				     created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON DUPLICATE KEY UPDATE url = VALUES(url), events = VALUES(events), signing_secret = VALUES(signing_secret),
				    previous_signing_secret = VALUES(previous_signing_secret),
				    previous_secret_until = VALUES(previous_secret_until), enabled = VALUES(enabled),
				    updated_at = VALUES(updated_at)""",
				endpoint.providerId(), endpoint.url().toString(), events(endpoint.events()), endpoint.signingSecret(),
				endpoint.previousSigningSecret(), timestamp(endpoint.previousSecretUntil()), endpoint.enabled(), now, now);
	}

	private static WebhookEndpoint endpoint(ResultSet row, int rowNum) throws SQLException {
		Timestamp until = row.getTimestamp("previous_secret_until");
		return new WebhookEndpoint(
				row.getLong("provider_id"),
				URI.create(row.getString("url")),
				events(row.getString("events")),
				row.getString("signing_secret"),
				row.getString("previous_signing_secret"),
				until == null ? null : until.toInstant(),
				row.getBoolean("enabled"));
	}

	private static String events(Set<WebhookEvent> events) {
		return events.stream().map(WebhookEvent::wireName).sorted().collect(Collectors.joining(","));
	}

	private static Set<WebhookEvent> events(String column) {
		return column.isBlank() ? Set.of()
				: Arrays.stream(column.split(",")).map(WebhookEvent::fromWireName).collect(Collectors.toSet());
	}

	private static Timestamp timestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}
}
