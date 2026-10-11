package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import sg.nus.carelink.visit.domain.repository.VisitRequestKeys;

/** {@link VisitRequestKeys} in {@code visit_request}, whose key is its primary key. */
@Repository
class JdbcVisitRequestKeys implements VisitRequestKeys {

	private final JdbcClient jdbc;

	JdbcVisitRequestKeys(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Made> find(String requestKey) {
		return jdbc.sql("select request_key, visit_id, result_visit_id from visit_request where request_key = :key")
				.param("key", requestKey)
				.query((row, number) -> new Made(row.getString("request_key"), row.getLong("visit_id"),
						row.getObject("result_visit_id", Long.class)))
				.optional();
	}

	@Override
	public void remember(Made made, LocalDateTime at) {
		jdbc.sql("insert into visit_request (request_key, visit_id, result_visit_id, created_at)"
						+ " values (:key, :visitId, :resultVisitId, :at)")
				.param("key", made.requestKey())
				.param("visitId", made.visitId())
				.param("resultVisitId", made.resultVisitId())
				.param("at", Timestamp.valueOf(at))
				.update();
	}
}
