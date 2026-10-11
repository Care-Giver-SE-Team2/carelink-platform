package sg.nus.carelink.incident.infrastructure.lookup;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.incident.domain.repository.SpotCheckLookups;

/**
 * What UC-MG08 looks up beside the spot check: the visits it is about, from visit's internal API
 * ({@link SpotCheckVisits}), and family_member and when the family was last asked (which the alert
 * notes on the check) with plain statements that take an id.
 *
 * <p>Times come out as {@link Timestamp}, never as {@link LocalDateTime}: only a Timestamp lands on
 * the same side of the connection's time-zone conversion as what JPA writes.
 */
@Component
class JdbcSpotCheckLookups implements SpotCheckLookups {

	private static final String FAMILY_MEMBER = "select id from family_member where user_id = :userId";

	/** The alert records when it asked the family: the request, and any reminder since. */
	private static final String LAST_ASKED = "select family_asked_at from spot_check where id = :checkId";

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
		return jdbc.sql(LAST_ASKED).param("checkId", checkId).query(Timestamp.class).optional()
				.map(Timestamp::toLocalDateTime);
	}
}
