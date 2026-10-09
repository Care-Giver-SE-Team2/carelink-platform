package sg.nus.carelink.rostering.infrastructure.notify;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.repository.RosterAudit;

/**
 * Writes the default plan into audit_log, where audits are read. No actor: nobody acted, the
 * family's time ran out. The moment is a {@link Timestamp} from the application's clock, for the
 * reason the incident module's alerts give: it lands where the application's own writes do.
 */
@Component
class AuditLogRosterAudit implements RosterAudit {

	private static final String INSERT = """
			insert into audit_log (actor_user_id, action, resource_type, resource_id, result, detail, occurred_at)
			values (null, 'DEFAULT_PLAN_APPLIED', 'roster_change', :changeId, 'OK', :detail, :occurredAt)
			""";

	private final JdbcClient jdbc;
	private final Clock clock;

	AuditLogRosterAudit(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public void defaultPlanApplied(RosterChange change, String detail) {
		jdbc.sql(INSERT)
				.param("changeId", change.id())
				.param("detail", detail == null || detail.length() <= 500 ? detail : detail.substring(0, 497) + "...")
				.param("occurredAt", Timestamp.valueOf(LocalDateTime.now(clock)))
				.update();
	}
}
