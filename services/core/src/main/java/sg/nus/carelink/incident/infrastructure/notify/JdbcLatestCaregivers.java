package sg.nus.carelink.incident.infrastructure.notify;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import sg.nus.carelink.platform.VisitInCore;

/**
 * {@link LatestCaregivers} from the visit table, with a plain statement that takes an id, while
 * visit runs inside core. It reads the table instead of calling visit's classes because visit
 * already depends on incident, and a dependency back would make the modules a cycle. Deleted
 * when visit moves out; {@link VisitApiLatestCaregivers} takes over.
 */
@Component
@VisitInCore
class JdbcLatestCaregivers implements LatestCaregivers {

	private static final String LATEST = """
			select caregiver_id from visit
			where elder_id = :elderId and caregiver_id is not null
			order by scheduled_start desc
			limit 1
			""";

	private final JdbcClient jdbc;

	JdbcLatestCaregivers(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Long> latestCaregiverId(Long elderId) {
		return jdbc.sql(LATEST).param("elderId", elderId).query(Long.class).optional();
	}

}
