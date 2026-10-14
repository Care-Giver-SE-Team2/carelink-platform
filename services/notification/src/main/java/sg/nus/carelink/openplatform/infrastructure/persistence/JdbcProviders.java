package sg.nus.carelink.openplatform.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.openplatform.domain.model.Provider;
import sg.nus.carelink.openplatform.domain.repository.Providers;

/**
 * {@code provider}. A change is a conditional update on the state it was read in (its version, client,
 * signing secret's issue and whether its secrets were collected), so two writers cannot both move a
 * provider on: the second changes nothing and is told so.
 */
@Repository
class JdbcProviders implements Providers {

	private static final String COLUMNS = """
			id, organisation_name, contact_email, services, portal_user_id, client_id, client_secret_hint,
			client_secret_issued_at, uncollected_client_secret, uncollected_webhook_secret, webhook_secret_issued_at,
			credentials_collected_at, status, version, approved_at""";

	private static final TypeReference<List<String>> SERVICES = new TypeReference<>() {
	};

	private final JdbcTemplate jdbc;
	private final JsonMapper json;
	private final Clock clock;

	JdbcProviders(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	@Override
	public Provider create(Provider provider) {
		jdbc.update("""
				INSERT INTO provider
				    (id, organisation_name, contact_email, services, portal_user_id, client_id, client_secret_hint,
				     client_secret_issued_at, uncollected_client_secret, uncollected_webhook_secret,
				     webhook_secret_issued_at, credentials_collected_at, status, version, approved_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
				provider.id(), provider.organisationName(), provider.contactEmail(),
				json.writeValueAsString(provider.services()), provider.portalUserId(), provider.clientId(),
				provider.clientSecretHint(), timestamp(provider.clientSecretIssuedAt()),
				provider.uncollectedClientSecret(), provider.uncollectedWebhookSecret(),
				timestamp(provider.webhookSecretIssuedAt()), timestamp(provider.credentialsCollectedAt()),
				provider.status().name(), provider.version(), timestamp(provider.approvedAt()),
				Timestamp.from(clock.instant()));
		return find(provider.id()).orElseThrow();
	}

	@Override
	public Optional<Provider> find(long id) {
		return jdbc.query("SELECT " + COLUMNS + " FROM provider WHERE id = ?", this::provider, id).stream().findFirst();
	}

	@Override
	public Optional<Provider> findByPortalUser(String portalUserId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM provider WHERE portal_user_id = ?", this::provider, portalUserId)
				.stream().findFirst();
	}

	@Override
	public List<Provider> all() {
		return jdbc.query("SELECT " + COLUMNS + " FROM provider ORDER BY approved_at DESC, id DESC", this::provider);
	}

	@Override
	public boolean update(Provider before, Provider after) {
		if (before.id() != after.id()) {
			throw new IllegalArgumentException("before and after are different providers");
		}
		return jdbc.update("""
				UPDATE provider
				SET services = ?, client_id = ?, client_secret_hint = ?, client_secret_issued_at = ?,
				    uncollected_client_secret = ?, uncollected_webhook_secret = ?, webhook_secret_issued_at = ?,
				    credentials_collected_at = ?, status = ?, version = ?, updated_at = ?
				WHERE id = ? AND version = ? AND client_id = ? AND webhook_secret_issued_at = ?
				  AND (credentials_collected_at IS NULL) = ?""",
				json.writeValueAsString(after.services()), after.clientId(), after.clientSecretHint(),
				timestamp(after.clientSecretIssuedAt()), after.uncollectedClientSecret(),
				after.uncollectedWebhookSecret(), timestamp(after.webhookSecretIssuedAt()),
				timestamp(after.credentialsCollectedAt()), after.status().name(), after.version(),
				Timestamp.from(clock.instant()),
				before.id(), before.version(), before.clientId(), timestamp(before.webhookSecretIssuedAt()),
				!before.collected()) == 1;
	}

	private Provider provider(ResultSet row, int rowNum) throws SQLException {
		return new Provider(
				row.getLong("id"),
				row.getString("organisation_name"),
				row.getString("contact_email"),
				json.readValue(row.getString("services"), SERVICES),
				row.getString("portal_user_id"),
				row.getString("client_id"),
				row.getString("client_secret_hint"),
				instant(row, "client_secret_issued_at"),
				row.getString("uncollected_client_secret"),
				row.getString("uncollected_webhook_secret"),
				instant(row, "webhook_secret_issued_at"),
				instant(row, "credentials_collected_at"),
				Provider.Status.valueOf(row.getString("status")),
				row.getInt("version"),
				instant(row, "approved_at"));
	}

	private static Instant instant(ResultSet row, String column) throws SQLException {
		Timestamp value = row.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private static Timestamp timestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}
}
