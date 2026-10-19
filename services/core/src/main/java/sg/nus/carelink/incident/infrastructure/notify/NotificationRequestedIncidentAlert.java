package sg.nus.carelink.incident.infrastructure.notify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.NotificationRequested;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.repository.IncidentAlert;
import sg.nus.carelink.shared.security.Role;

/**
 * Sends staff alerts as {@code NotificationRequested} events, one per recipient, in the
 * transaction that raised or moved the incident; notification keeps each one for its recipient's
 * in-app inbox, where it waits as PENDING until it is read.
 *
 * <p>Reads {@code user_role} with a plain statement, and asks visit for the caregiver on the
 * elder's most recent visit, or for a missed check-in the caregiver on the triggering visit
 * ({@link LatestCaregivers}). Reading is not
 * owning; no other module's code changes for this to work. Family messages are created only
 * by the after-commit FM05 observer, so this adapter never produces a second family copy.
 */
@Component
class NotificationRequestedIncidentAlert implements IncidentAlert {

	private static final String MANAGERS = """
			select u.id from app_user u
			join user_role r on r.user_id = u.id
			where u.enabled = true and r.role = :role
			""";

	/** The account of a caregiver: an alert reaches a caregiver through their account. */
	private static final String CAREGIVER_ACCOUNT = "select user_id from caregiver where id = :caregiverId";

	/** As {@link #CAREGIVER_ACCOUNT}, but only while the account is enabled and still holds the caregiver role. */
	private static final String ACTIVE_CAREGIVER_ACCOUNT = """
			select c.user_id from caregiver c
			join app_user u on u.id = c.user_id
			where c.id = :caregiverId and u.enabled = true
			and exists (select 1 from user_role r where r.user_id = u.id and r.role = 'CAREGIVER')
			""";

	private final JdbcClient jdbc;
	private final LatestCaregivers caregivers;
	private final Events events;
	private final Clock clock;

	NotificationRequestedIncidentAlert(JdbcClient jdbc, LatestCaregivers caregivers, Events events, Clock clock) {
		this.jdbc = jdbc;
		this.caregivers = caregivers;
		this.events = events;
		this.clock = clock;
	}

	@Override
	public int broadcastRaised(Incident incident) {
		Set<Long> managerAudience = new LinkedHashSet<>(managers());
		Set<Long> audience = new LinkedHashSet<>(managerAudience);
		// SYS03 is raised while visit holds this exact visit locked. Never substitute a later
		// visit's caregiver; the trigger ledger visit saves preserves this assignment.
		boolean missedCheckIn = incident.source() == Incident.Source.SYSTEM_MISSED_CHECKIN;
		List<Long> caregivers = missedCheckIn ? assignedCaregiver(incident.visitId()) : recentCaregiver(incident.elderId());
		audience.addAll(caregivers);

		String title = "%s: %s".formatted(incident.severity(), incident.category());
		String body = "%s. %s".formatted(
				describeSource(incident.source()),
				incident.description() == null ? "No description given." : incident.description());

		for (Long userId : audience) {
			if (missedCheckIn && caregivers.contains(userId) && !managerAudience.contains(userId)) {
				write(userId, "INCIDENT_RAISED", "Missed check-in alert",
						"Visit %d was not checked in by the allowed threshold. This alert does not block service within the check-in window."
								.formatted(incident.visitId()),
						incident, "CAREGIVER_SYSTEM_ALERT");
			}
			else {
				write(userId, "INCIDENT_RAISED", title, body, incident);
			}
		}
		return audience.size();
	}

	@Override
	public void handedOver(Incident incident, Long fromUserId, Long toUserId) {
		if (toUserId != null) {
			write(toUserId, "INCIDENT_ASSIGNED",
					"You are now responsible for incident %d".formatted(incident.id()),
					"Respond by %s.".formatted(incident.respondBy()), incident);
		}
		if (fromUserId != null && !fromUserId.equals(toUserId)) {
			write(fromUserId, "INCIDENT_MOVED_ON",
					"Incident %d has moved to another responder".formatted(incident.id()),
					"Your record of it stays on the timeline.", incident);
		}
	}

	// -------------------------------------------------------------------- queries ---

	private List<Long> managers() {
		return jdbc.sql(MANAGERS).param("role", Role.MANAGER.name()).query(Long.class).list();
	}

	private List<Long> assignedCaregiver(Long visitId) {
		if (visitId == null) {
			return List.of();
		}
		return caregivers.assignedCaregiverId(visitId)
				.map(caregiverId -> jdbc.sql(ACTIVE_CAREGIVER_ACCOUNT).param("caregiverId", caregiverId).query(Long.class).list())
				.orElse(List.of());
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

	private void write(Long userId, String eventType, String title, String body, Incident incident) {
		write(userId, eventType, title, body, incident, "INCIDENT");
	}

	private void write(Long userId, String eventType, String title, String body, Incident incident,
			String resourceType) {
		events.publish(NotificationRequested.TYPE, NotificationRequested.inApp(userId, eventType, title, body,
				resourceType, incident.id(), incident.elderId(), LocalDateTime.now(clock)));
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
