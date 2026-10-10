package sg.nus.carelink.visit.infrastructure.lookup;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import sg.nus.carelink.visit.application.VisitLookups;

/**
 * {@link VisitLookups} with the statements incident runs today (in JdbcSpotCheckLookups and
 * NotificationTableAlert), so the answers do not change when incident starts asking visit.
 *
 * <p>Times go in and come out as {@link Timestamp}, the way JPA writes visit times, so they land on
 * the same side of the connection's time-zone conversion.
 */
@Component
class JdbcVisitLookups implements VisitLookups {

	private static final String UPCOMING = """
			select id, elder_id, caregiver_id, scheduled_start, status, service_type
			from visit
			where elder_id = :elderId and status = 'SCHEDULED' and caregiver_id is not null
			  and scheduled_start >= :fromTime and scheduled_start < :untilTime
			order by scheduled_start, id
			""";

	private static final String LATEST_CAREGIVER = """
			select caregiver_id from visit
			where elder_id = :elderId and caregiver_id is not null
			order by scheduled_start desc
			limit 1
			""";

	private final JdbcClient jdbc;

	JdbcVisitLookups(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<ElderVisit> upcomingVisits(Long elderId, LocalDateTime from, LocalDateTime until) {
		return jdbc.sql(UPCOMING)
				.param("elderId", elderId)
				.param("fromTime", Timestamp.valueOf(from))
				.param("untilTime", Timestamp.valueOf(until))
				.query(JdbcVisitLookups::elderVisit)
				.list();
	}

	@Override
	public Optional<Long> latestCaregiverId(Long elderId) {
		return jdbc.sql(LATEST_CAREGIVER).param("elderId", elderId).query(Long.class).optional();
	}

	private static ElderVisit elderVisit(ResultSet rs, int row) throws SQLException {
		return new ElderVisit(rs.getLong("id"), rs.getLong("elder_id"), rs.getLong("caregiver_id"),
				rs.getTimestamp("scheduled_start").toLocalDateTime(), rs.getString("status"), rs.getString("service_type"));
	}

}
