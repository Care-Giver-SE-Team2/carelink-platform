package sg.nus.carelink.incident.infrastructure.lookup;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.incident.domain.repository.SpotCheckLookups;

/**
 * What UC-MG08 looks up outside the spot check: the visits it is about, from visit
 * ({@link SpotCheckVisits}), and family_member and the family's asks with plain statements that
 * take an id.
 *
 * <p>Times come out as {@link Timestamp}, never as {@link LocalDateTime}: only a Timestamp lands on
 * the same side of the connection's time-zone conversion as what JPA writes (see
 * NotificationTableAlert for how that was found).
 */
@Component
class JdbcSpotCheckLookups implements SpotCheckLookups {

	private static final String FAMILY_MEMBER = "select id from family_member where user_id = :userId";

	/** The family's asks are the notifications the alert wrote: the request, and any reminders. */
	private static final String LAST_ASKED = """
			select max(created_at) from notification
			where resource_type = 'SPOT_CHECK' and resource_id = :checkId
			  and event_type in ('SPOT_CHECK_REQUESTED', 'SPOT_CHECK_REMINDER')
			""";

	private final JdbcClient jdbc;
	private final SpotCheckVisits visits;

	JdbcSpotCheckLookups(JdbcClient jdbc, SpotCheckVisits visits) {
		this.jdbc = jdbc;
		this.visits = visits;
	}

	@Override
	public Optional<VisitFacts> visit(Long visitId) {
		return visits.visit(visitId);
	}

	@Override
	public List<VisitFacts> upcomingVisits(Long elderId, LocalDateTime from, LocalDateTime until) {
		return visits.upcomingVisits(elderId, from, until);
	}

	@Override
	public Optional<Long> familyMemberIdOf(Long userId) {
		return jdbc.sql(FAMILY_MEMBER).param("userId", userId).query(Long.class).optional();
	}

	@Override
	public Optional<LocalDateTime> lastAskedAt(Long checkId) {
		return Optional.ofNullable(jdbc.sql(LAST_ASKED).param("checkId", checkId).query(Timestamp.class).single())
				.map(Timestamp::toLocalDateTime);
	}
}
