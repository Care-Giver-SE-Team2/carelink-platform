package sg.nus.carelink.incident.infrastructure.lookup;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import sg.nus.carelink.incident.domain.repository.SpotCheckLookups.VisitFacts;
import sg.nus.carelink.platform.VisitInCore;

/**
 * {@link SpotCheckVisits} from the visit table, with plain statements that take an id, while
 * visit runs inside core. It reads the table instead of calling visit's classes because visit
 * already depends on incident, and a dependency back would make the modules a cycle. Deleted
 * when visit moves out; {@link VisitApiSpotCheckVisits} takes over.
 *
 * <p>Times go in and come out as {@link Timestamp}, never as {@link LocalDateTime}: visit times
 * are written through JPA, which binds a Timestamp, and only a Timestamp lands on the same side
 * of the connection's time-zone conversion (see NotificationTableAlert for how that was found).
 */
@Component
@VisitInCore
class JdbcSpotCheckVisits implements SpotCheckVisits {

	private static final String VISIT = """
			select id, elder_id, caregiver_id, scheduled_start, status, service_type
			from visit where id = :visitId
			""";

	private static final String UPCOMING = """
			select id, elder_id, caregiver_id, scheduled_start, status, service_type
			from visit
			where elder_id = :elderId and status = 'SCHEDULED' and caregiver_id is not null
			  and scheduled_start >= :fromTime and scheduled_start < :untilTime
			order by scheduled_start, id
			""";

	private final JdbcClient jdbc;

	JdbcSpotCheckVisits(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<VisitFacts> visit(Long visitId) {
		return jdbc.sql(VISIT).param("visitId", visitId).query(JdbcSpotCheckVisits::visitFacts).optional();
	}

	@Override
	public List<VisitFacts> upcomingVisits(Long elderId, LocalDateTime from, LocalDateTime until) {
		return jdbc.sql(UPCOMING)
				.param("elderId", elderId)
				.param("fromTime", Timestamp.valueOf(from))
				.param("untilTime", Timestamp.valueOf(until))
				.query(JdbcSpotCheckVisits::visitFacts)
				.list();
	}

	private static VisitFacts visitFacts(ResultSet rs, int row) throws SQLException {
		long caregiverId = rs.getLong("caregiver_id");
		Long caregiver = rs.wasNull() ? null : caregiverId;
		Timestamp start = rs.getTimestamp("scheduled_start");
		return new VisitFacts(rs.getLong("id"), rs.getLong("elder_id"), caregiver,
				start == null ? null : start.toLocalDateTime(), rs.getString("status"), rs.getString("service_type"));
	}

}
