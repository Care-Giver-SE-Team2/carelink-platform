package sg.nus.carelink.report.infrastructure.openplatform;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.report.domain.model.Provider;
import sg.nus.carelink.report.domain.repository.Providers;

/** {@link Providers} in {@code report_provider} and {@code report_provider_service} (migration V3). */
@Component
class JdbcProviders implements Providers {

	private static final String COLUMNS = "provider_id, client_id, organisation_name, status, version";

	private final JdbcClient jdbc;

	JdbcProviders(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void keep(Provider provider) {
		Integer held = jdbc.sql("select version from report_provider where provider_id = ? for update")
				.param(provider.id())
				.query(Integer.class)
				.optional()
				.orElse(null);
		if (held != null && held >= provider.version()) {
			return;
		}
		if (provider.clientId() != null) {
			// A rotated secret's client is never reused, but a copy that lags may still name it
			jdbc.sql("update report_provider set client_id = null where client_id = ? and provider_id <> ?")
					.params(provider.clientId(), provider.id())
					.update();
		}
		jdbc.sql("""
				insert into report_provider (provider_id, client_id, organisation_name, status, version)
				values (:id, :clientId, :name, :status, :version)
				on duplicate key update client_id = :clientId, organisation_name = :name, status = :status,
				    version = :version
				""")
				.param("id", provider.id())
				.param("clientId", provider.clientId())
				.param("name", provider.organisationName())
				.param("status", provider.status().name())
				.param("version", provider.version())
				.update();
		jdbc.sql("delete from report_provider_service where provider_id = ?").param(provider.id()).update();
		for (String service : provider.services()) {
			jdbc.sql("insert into report_provider_service (provider_id, service_name) values (?, ?)")
					.params(provider.id(), service)
					.update();
		}
	}

	@Override
	public Optional<Provider> find(Long providerId) {
		return jdbc.sql("select " + COLUMNS + " from report_provider where provider_id = ?")
				.param(providerId)
				.query(this::row)
				.optional();
	}

	@Override
	public Optional<Provider> findByClientId(String clientId) {
		return jdbc.sql("select " + COLUMNS + " from report_provider where client_id = ?")
				.param(clientId)
				.query(this::row)
				.optional();
	}

	@Override
	public Optional<Provider> nextFor(String serviceName) {
		return jdbc.sql("""
				select p.provider_id, p.client_id, p.organisation_name, p.status, p.version
				from report_provider p
				join report_provider_service s on s.provider_id = p.provider_id and s.service_name = ?
				left join (select provider_id, max(dispatched_at) as last_dispatched
				           from report_provider_job group by provider_id) j on j.provider_id = p.provider_id
				where p.status = 'ACTIVE' and p.client_id is not null
				order by j.last_dispatched is not null, j.last_dispatched, p.provider_id
				limit 1
				""")
				.param(serviceName)
				.query(this::row)
				.optional();
	}

	private Provider row(ResultSet rs, int rowNum) throws SQLException {
		long id = rs.getLong("provider_id");
		List<String> services = jdbc
				.sql("select service_name from report_provider_service where provider_id = ? order by service_name")
				.param(id)
				.query(String.class)
				.list();
		return new Provider(id, rs.getString("client_id"), rs.getString("organisation_name"),
				Provider.Status.valueOf(rs.getString("status")), services, rs.getInt("version"));
	}
}
