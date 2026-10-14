package sg.nus.carelink.openplatform.infrastructure.persistence;

import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.openplatform.domain.model.ApplicationDetails;
import sg.nus.carelink.openplatform.domain.model.ProviderApplication;
import sg.nus.carelink.openplatform.domain.repository.ProviderApplications;

/**
 * {@code provider_application}. The services are kept as a JSON array; times are bound as JDBC
 * Timestamps, which the connection's Singapore time zone turns into the wall-clock times the columns keep.
 */
@Repository
class JdbcProviderApplications implements ProviderApplications {

	private static final String COLUMNS = """
			id, organisation_name, contact_name, contact_email, contact_phone, services, webhook_url, portal_user_id,
			status, reason, submitted_at, reviewed_at, reviewed_by, provider_id""";

	private static final TypeReference<List<String>> SERVICES = new TypeReference<>() {
	};

	private final JdbcTemplate jdbc;
	private final JsonMapper json;

	JdbcProviderApplications(JdbcTemplate jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	public ProviderApplication create(ProviderApplication application) {
		ApplicationDetails details = application.details();
		GeneratedKeyHolder key = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement insert = connection.prepareStatement("""
					INSERT INTO provider_application
					    (organisation_name, contact_name, contact_email, contact_phone, services, webhook_url,
					     portal_user_id, status, submitted_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS);
			insert.setString(1, details.organisationName());
			insert.setString(2, details.contactName());
			insert.setString(3, details.contactEmail());
			insert.setString(4, details.contactPhone());
			insert.setString(5, json.writeValueAsString(details.services()));
			insert.setString(6, details.webhookUrl().toString());
			insert.setString(7, application.portalUserId());
			insert.setString(8, application.status().name());
			insert.setTimestamp(9, Timestamp.from(application.submittedAt()));
			return insert;
		}, key);
		return find(key.getKey().longValue()).orElseThrow();
	}

	@Override
	public Optional<ProviderApplication> find(long id) {
		return jdbc.query("SELECT " + COLUMNS + " FROM provider_application WHERE id = ?", this::application, id)
				.stream().findFirst();
	}

	@Override
	public Optional<ProviderApplication> latestOf(String portalUserId) {
		return jdbc.query("SELECT " + COLUMNS
				+ " FROM provider_application WHERE portal_user_id = ? ORDER BY submitted_at DESC, id DESC LIMIT 1",
				this::application, portalUserId).stream().findFirst();
	}

	@Override
	public boolean emailHeld(String contactEmail) {
		Integer held = jdbc.queryForObject("""
				SELECT COUNT(*) FROM provider_application
				WHERE contact_email = ? AND status IN ('PENDING', 'APPROVED')""", Integer.class, contactEmail);
		return held != null && held > 0;
	}

	@Override
	public List<ProviderApplication> pending() {
		return jdbc.query("SELECT " + COLUMNS
				+ " FROM provider_application WHERE status = 'PENDING' ORDER BY submitted_at DESC, id DESC",
				this::application);
	}

	@Override
	public boolean decide(ProviderApplication decided) {
		return jdbc.update("""
				UPDATE provider_application
				SET status = ?, reason = ?, reviewed_at = ?, reviewed_by = ?, provider_id = ?
				WHERE id = ? AND status = 'PENDING'""",
				decided.status().name(), decided.reason(), timestamp(decided.reviewedAt()), decided.reviewedBy(),
				decided.providerId(), decided.id()) == 1;
	}

	private ProviderApplication application(ResultSet row, int rowNum) throws SQLException {
		ApplicationDetails details = new ApplicationDetails(
				row.getString("organisation_name"),
				row.getString("contact_name"),
				row.getString("contact_email"),
				row.getString("contact_phone"),
				json.readValue(row.getString("services"), SERVICES),
				URI.create(row.getString("webhook_url")));
		Timestamp reviewedAt = row.getTimestamp("reviewed_at");
		return new ProviderApplication(
				row.getLong("id"),
				details,
				row.getString("portal_user_id"),
				ProviderApplication.Status.valueOf(row.getString("status")),
				row.getString("reason"),
				row.getTimestamp("submitted_at").toInstant(),
				reviewedAt == null ? null : reviewedAt.toInstant(),
				row.getObject("reviewed_by", Long.class),
				row.getObject("provider_id", Long.class));
	}

	private static Timestamp timestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}
}
