package sg.nus.carelink.incident.infrastructure.notify;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.repository.IncidentAlert;
import sg.nus.carelink.shared.security.Role;

/**
 * Writes alerts as rows in {@code notification}, which is what the in-app inbox reads.
 *
 * <p>Nothing sends these anywhere. Push, SMS and e-mail would need a component that polls
 * this table, calls whatever carries the message, and moves the row to SENT or FAILED with
 * a retry; that is SUP-02 notification dispatch (use case specification v3.0 §2.2), and
 * nobody has taken it on. Until somebody does, a row with {@code status = PENDING} is the
 * honest state: the alert exists and is addressed, and whoever builds the sender will find
 * it waiting rather than having to work out the audience again.
 *
 * <p>Reads three tables it does not own - {@code user_role}, {@code elder_family_binding},
 * {@code visit} - with plain statements that take an id and nothing else. Reading is not
 * owning; no other module's code changes for this to work.
 */
@Component
class NotificationTableAlert implements IncidentAlert {

	private static final String MANAGERS = """
			select u.id from app_user u
			join user_role r on r.user_id = u.id
			where u.enabled = true and r.role = :role
			""";

	/**
	 * Family members with a binding that is active now, through to their account.
	 *
	 * <p>The moment is bound as a {@link Timestamp} made from the application's clock, and
	 * both halves of that matter. Not SQL {@code now()}: that is the database's own clock, in
	 * the database's own zone. And not a {@link LocalDateTime}: the JVM runs in UTC while the
	 * JDBC connection declares Asia/Singapore, and Connector/J converts a {@code Timestamp}
	 * between the two but sends a {@code LocalDateTime} exactly as it is. {@code expires_at}
	 * is written through JPA, which binds a {@code Timestamp}, so it sits in the column eight
	 * hours from its nominal value. Only a {@code Timestamp} parameter lands on the same side
	 * of that shift; a {@code LocalDateTime} kept an expired binding receiving alerts for eight
	 * more hours, which is what this code did until the report module's queries showed the
	 * two parameter types are not treated alike.
	 */
	private static final String BOUND_FAMILY = """
			select f.user_id from elder_family_binding b
			join family_member f on f.id = b.family_member_id
			where b.elder_id = :elderId
			  and b.status = 'ACTIVE'
			  and (b.expires_at is null or b.expires_at > :now)
			  and f.user_id is not null
			""";

	/** The caregiver on this elder's most recent visit, if there has been one. */
	private static final String RECENT_CAREGIVER = """
			select c.user_id from visit v
			join caregiver c on c.id = v.caregiver_id
			where v.elder_id = :elderId and v.caregiver_id is not null
			order by v.scheduled_start desc
			limit 1
			""";

	private static final String INSERT = """
			insert into notification
			    (recipient_user_id, event_type, channel, title, body, resource_type, resource_id, status,
			     created_at)
			values (:userId, :eventType, 'IN_APP', :title, :body, 'INCIDENT', :incidentId, 'PENDING',
			        :createdAt)
			""";

	private final JdbcClient jdbc;
	private final Clock clock;

	NotificationTableAlert(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public int broadcastRaised(Incident incident) {
		Set<Long> audience = new LinkedHashSet<>(managers());
		audience.addAll(boundFamily(incident.elderId()));
		audience.addAll(recentCaregiver(incident.elderId()));

		String title = "%s: %s".formatted(incident.severity(), incident.category());
		String body = "%s. %s".formatted(
				describeSource(incident.source()),
				incident.description() == null ? "No description given." : incident.description());

		for (Long userId : audience) {
			write(userId, "INCIDENT_RAISED", title, body, incident.id());
		}
		return audience.size();
	}

	@Override
	public void handedOver(Incident incident, Long fromUserId, Long toUserId) {
		if (toUserId != null) {
			write(toUserId, "INCIDENT_ASSIGNED",
					"You are now responsible for incident %d".formatted(incident.id()),
					"Respond by %s.".formatted(incident.respondBy()), incident.id());
		}
		if (fromUserId != null && !fromUserId.equals(toUserId)) {
			write(fromUserId, "INCIDENT_MOVED_ON",
					"Incident %d has moved to another responder".formatted(incident.id()),
					"Your record of it stays on the timeline.", incident.id());
		}
	}

	@Override
	public void chainExhausted(Incident incident) {
		for (Long userId : boundFamily(incident.elderId())) {
			write(userId, "INCIDENT_UNRESOLVED",
					"Incident %d has not been taken up".formatted(incident.id()),
					"The institution has been unable to assign a responder. Please contact them directly.",
					incident.id());
		}
	}

	// -------------------------------------------------------------------- queries ---

	private List<Long> managers() {
		return jdbc.sql(MANAGERS).param("role", Role.MANAGER.name()).query(Long.class).list();
	}

	private List<Long> boundFamily(Long elderId) {
		return elderId == null
				? List.of()
				: jdbc.sql(BOUND_FAMILY)
						.param("elderId", elderId)
						.param("now", Timestamp.valueOf(LocalDateTime.now(clock)))
						.query(Long.class)
						.list();
	}

	private List<Long> recentCaregiver(Long elderId) {
		if (elderId == null) {
			return List.of();
		}
		List<Long> found = new ArrayList<>(
				jdbc.sql(RECENT_CAREGIVER).param("elderId", elderId).query(Long.class).list());
		found.removeIf(java.util.Objects::isNull);
		return found;
	}

	private void write(Long userId, String eventType, String title, String body, Long incidentId) {
		jdbc.sql(INSERT)
				.param("userId", userId)
				.param("eventType", eventType)
				.param("title", trim(title, 150))
				.param("body", trim(body, 1000))
				.param("incidentId", incidentId)
				// Written here rather than left to the column default: the default is the
				// database's clock, which JPA would read back sixteen hours out. A Timestamp from
				// the application's clock lands where the application's own writes do.
				.param("createdAt", Timestamp.valueOf(LocalDateTime.now(clock)))
				.update();
	}

	private static String trim(String text, int max) {
		if (text == null) {
			return null;
		}
		return text.length() <= max ? text : text.substring(0, max - 3) + "...";
	}

	private static String describeSource(Incident.Source source) {
		return switch (source) {
			case ELDER_SOS -> "Raised by the elder's emergency button";
			case CAREGIVER -> "Reported by a caregiver";
			case ELDER_SERVICE_DISPUTE -> "Raised by the elder after disputing a completed service";
			case SYSTEM_MISSED_CHECKIN -> "Raised automatically by a missed check-in";
		};
	}
}
