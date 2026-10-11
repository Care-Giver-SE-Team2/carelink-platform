package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.incident.domain.repository.IncidentRequestKeys;

/** {@link IncidentRequestKeys} in {@code incident_request}, whose key is its primary key. */
@Component
class JdbcIncidentRequestKeys implements IncidentRequestKeys {

	private final JdbcClient jdbc;

	JdbcIncidentRequestKeys(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Long> incidentFor(String requestKey) {
		return jdbc.sql("select incident_id from incident_request where request_key = :key")
				.param("key", requestKey).query(Long.class).optional();
	}

	@Override
	public void remember(String requestKey, Long incidentId, LocalDateTime at) {
		jdbc.sql("insert into incident_request (request_key, incident_id, created_at) values (:key, :incidentId, :at)")
				.param("key", requestKey).param("incidentId", incidentId).param("at", Timestamp.valueOf(at)).update();
	}
}
