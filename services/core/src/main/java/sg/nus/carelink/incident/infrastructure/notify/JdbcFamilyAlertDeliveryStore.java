package sg.nus.carelink.incident.infrastructure.notify;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.incident.domain.model.FamilyAlertEvent;
import sg.nus.carelink.incident.domain.model.FamilyUrgentNotice;
import sg.nus.carelink.incident.domain.repository.FamilyAlertDeliveryStore;

/** FM05-owned writes alongside the existing inbox, without altering its delivery implementation. @author Wang Zhili */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcFamilyAlertDeliveryStore implements FamilyAlertDeliveryStore {
	private final JdbcTemplate jdbc;
	JdbcFamilyAlertDeliveryStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override public void register(FamilyAlertEvent event, LocalDateTime now) {
		jdbc.update("""
				INSERT INTO family_alert_event (event_id, event_type, incident_id, elder_id, occurred_at, state, last_attempt_at)
				VALUES (?, ?, ?, ?, ?, 'PENDING', ?) ON DUPLICATE KEY UPDATE event_id = event_id
				""", event.eventId().toString(), event.type().name(), event.incidentId(), event.elderId(),
				time(event.occurredAt().toLocalDateTime()), time(now));
		FamilyAlertEvent existing = jdbc.queryForObject("SELECT * FROM family_alert_event WHERE event_id = ? FOR UPDATE",
				(rs, row) -> new FamilyAlertEvent(UUID.fromString(rs.getString("event_id")), FamilyAlertEvent.Type.valueOf(rs.getString("event_type")),
						rs.getLong("incident_id"), rs.getLong("elder_id"), rs.getTimestamp("occurred_at").toLocalDateTime().atOffset(ZoneOffset.ofHours(8))),
				event.eventId().toString());
		if (!event.equals(existing)) { throw new IllegalArgumentException("An eventId cannot be reused for different facts"); }
	}

	@Override public boolean alreadyCreated(UUID eventId, Long familyId, LocalDateTime now) {
		jdbc.update("""
				INSERT INTO family_alert_delivery (event_id, family_member_id, status, attempted_at)
				VALUES (?, ?, 'PROCESSING', ?) ON DUPLICATE KEY UPDATE event_id = event_id
				""", eventId.toString(), familyId, time(now));
		String status = jdbc.queryForObject("SELECT status FROM family_alert_delivery WHERE event_id = ? AND family_member_id = ? FOR UPDATE",
				String.class, eventId.toString(), familyId);
		return "CREATED".equals(status);
	}

	@Override public void skipped(UUID eventId, Long familyId, Long accountId, String reason, LocalDateTime now) {
		jdbc.update("""
				UPDATE family_alert_delivery SET recipient_user_id = ?, status = 'SKIPPED', reason = ?, attempted_at = ?
				WHERE event_id = ? AND family_member_id = ?
				""", accountId, reason, time(now), eventId.toString(), familyId);
	}

	@Override public void create(FamilyAlertEvent event, Long familyId, Long accountId, FamilyUrgentNotice notice) {
		var keys = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			var statement = connection.prepareStatement("""
					INSERT INTO notification (recipient_user_id, event_type, channel, title, body, resource_type, resource_id, status, created_at)
					VALUES (?, ?, 'IN_APP', ?, ?, 'INCIDENT', ?, 'PENDING', ?)
					""", Statement.RETURN_GENERATED_KEYS);
			statement.setLong(1, accountId); statement.setString(2, event.type().name());
			statement.setString(3, notice.title()); statement.setString(4, notice.body());
			statement.setLong(5, event.incidentId()); statement.setTimestamp(6, time(notice.createdAt()));
			return statement;
		}, keys);
		long notificationId = keys.getKey().longValue();
		jdbc.update("""
				INSERT INTO family_alert_window (incident_id, family_member_id, first_notification_id, opened_at, acknowledge_by)
				VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE incident_id = incident_id
				""", event.incidentId(), familyId, notificationId, time(notice.createdAt()), time(notice.acknowledgeBy()));
		jdbc.update("""
				UPDATE family_alert_delivery SET recipient_user_id = ?, notification_id = ?, status = 'CREATED', reason = NULL, attempted_at = ?
				WHERE event_id = ? AND family_member_id = ?
				""", accountId, notificationId, time(notice.createdAt()), event.eventId().toString(), familyId);
	}

	@Override public void failed(UUID eventId, Long familyId, LocalDateTime now) {
		// A concurrent successful retry must not be overwritten by this failed attempt's follow-up.
		jdbc.update("""
				INSERT INTO family_alert_delivery (event_id, family_member_id, status, reason, attempted_at)
				VALUES (?, ?, 'FAILED', 'RECIPIENT_PROCESSING_FAILED', ?)
				ON DUPLICATE KEY UPDATE reason = IF(status = 'CREATED', reason, VALUES(reason)),
					attempted_at = IF(status = 'CREATED', attempted_at, VALUES(attempted_at)),
					status = IF(status = 'CREATED', status, 'FAILED')
				""", eventId.toString(), familyId, time(now));
	}

	@Override public void complete(UUID eventId, EventState state, String reason, LocalDateTime now) {
		jdbc.update("UPDATE family_alert_event SET state = ?, reason = ?, last_attempt_at = ? WHERE event_id = ?",
				state.name(), reason, time(now), eventId.toString());
	}

	@Override public Optional<LocalDateTime> acknowledgeBy(Long incidentId, Long familyId) {
		return jdbc.query("SELECT acknowledge_by FROM family_alert_window WHERE incident_id = ? AND family_member_id = ?",
				(rs, row) -> rs.getTimestamp("acknowledge_by").toLocalDateTime(), incidentId, familyId).stream().findFirst();
	}
	private static Timestamp time(LocalDateTime value) { return Timestamp.valueOf(value); }
}
