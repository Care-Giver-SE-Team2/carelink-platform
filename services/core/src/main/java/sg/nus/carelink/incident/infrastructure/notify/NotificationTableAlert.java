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
 * Writes staff alerts as rows in {@code notification}, which is what the in-app inbox reads.
 *
 * <p>Nothing sends these anywhere. Push, SMS and e-mail would need a component that polls
 * this table, calls whatever carries the message, and moves the row to SENT or FAILED with
 * a retry; that is SUP-02 notification dispatch (use case specification v3.0 §2.2), and
 * nobody has taken it on. Until somebody does, a row with {@code status = PENDING} is the
 * honest state: the alert exists and is addressed, and whoever builds the sender will find
 * it waiting rather than having to work out the audience again.
 *
 * <p>Reads {@code user_role} with a plain statement, and asks visit for the caregiver on the
 * elder's most recent visit ({@link LatestCaregivers}). Reading is not
 * owning; no other module's code changes for this to work. Family messages are created only
 * by the after-commit FM05 observer, so this adapter never produces a second family copy.
 */
@Component
class NotificationTableAlert implements IncidentAlert {

	private static final String MANAGERS = """
			select u.id from app_user u
			join user_role r on r.user_id = u.id
			where u.enabled = true and r.role = :role
			""";

	/** The account of a caregiver: an alert reaches a caregiver through their account. */
	private static final String CAREGIVER_ACCOUNT = "select user_id from caregiver where id = :caregiverId";

	private static final String INSERT = """
			insert into notification
			    (recipient_user_id, event_type, channel, title, body, resource_type, resource_id, status,
			     created_at)
			values (:userId, :eventType, 'IN_APP', :title, :body, 'INCIDENT', :incidentId, 'PENDING',
			        :createdAt)
			""";

	private final JdbcClient jdbc;
	private final LatestCaregivers caregivers;
	private final Clock clock;

	NotificationTableAlert(JdbcClient jdbc, LatestCaregivers caregivers, Clock clock) {
		this.jdbc = jdbc;
		this.caregivers = caregivers;
		this.clock = clock;
	}

	@Override
	public int broadcastRaised(Incident incident) {
		Set<Long> audience = new LinkedHashSet<>(managers());
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

	// -------------------------------------------------------------------- queries ---

	private List<Long> managers() {
		return jdbc.sql(MANAGERS).param("role", Role.MANAGER.name()).query(Long.class).list();
	}

	private List<Long> recentCaregiver(Long elderId) {
		if (elderId == null) {
			return List.of();
		}
		List<Long> found = new ArrayList<>(caregivers.latestCaregiverId(elderId)
				.map(caregiverId -> jdbc.sql(CAREGIVER_ACCOUNT).param("caregiverId", caregiverId).query(Long.class).list())
				.orElse(List.of()));
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
