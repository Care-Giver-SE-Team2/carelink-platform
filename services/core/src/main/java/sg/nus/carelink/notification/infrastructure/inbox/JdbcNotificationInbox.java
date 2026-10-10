package sg.nus.carelink.notification.infrastructure.inbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.notification.domain.model.InboxItem;
import sg.nus.carelink.notification.domain.model.InboxReader;
import sg.nus.carelink.notification.domain.model.Notification;
import sg.nus.carelink.notification.domain.repository.NotificationInbox;

/**
 * The inbox in SQL. It reads tables other modules own - incident, roster_change, spot_check,
 * value_added_service_request and care_plan for the elder a message is about, elder_family_binding and
 * family_member for the family rule - with plain statements, the way every notifier finds its
 * recipients, so this module needs none of their code.
 *
 * <p>Times are bound and read as {@link Timestamp}, the way the notifiers write them and JPA
 * writes the bindings, so the connection's time-zone conversion is the same on both sides.
 */
@Component
class JdbcNotificationInbox implements NotificationInbox {

	private static final String COLUMNS = """
			select n.id, n.recipient_user_id, n.event_type, n.channel, n.title, n.body, n.resource_type,
			       n.resource_id, n.status, n.created_at, n.sent_at, n.read_at,
			       coalesce(i.elder_id, rc.elder_id, sc.elder_id, vasr.elder_id, cp.elder_id) as elder_id
			""";

	/** The reader's own in-app messages in the asked statuses, with what each links to. */
	private static final String FROM = """
			from notification n
			left join incident i on n.resource_type = 'INCIDENT' and i.id = n.resource_id
			left join roster_change rc on n.resource_type = 'ROSTER_CHANGE' and rc.id = n.resource_id
			left join spot_check sc on n.resource_type = 'SPOT_CHECK' and sc.id = n.resource_id
			left join value_added_service_request vasr on n.resource_type = 'VALUE_ADDED_REQUEST' and vasr.id = n.resource_id
			left join care_plan cp on n.resource_type = 'CARE_PLAN' and cp.id = n.resource_id
			where n.recipient_user_id = :userId
			  and n.channel = 'IN_APP'
			  and n.status in (:statuses)
			""";

	/**
	 * The family rule. A message about a care record is shown while the reader is bound to its
	 * elder - ACTIVE and not expired - and not otherwise; one whose elder cannot be found is not
	 * shown at all. Only a message with neither resource type nor resource ID is account-only;
	 * unknown resource types must not bypass the care-content authorization rule.
	 */
	private static final String FAMILY_SCOPE = """
			  and ((n.resource_type is null and n.resource_id is null)
			       or (n.resource_type in ('INCIDENT', 'ROSTER_CHANGE', 'SPOT_CHECK', 'VALUE_ADDED_REQUEST', 'CARE_PLAN')
			           and exists (select 1 from elder_family_binding b
			                  join family_member f on f.id = b.family_member_id
			                  where f.user_id = :userId
			                    and b.elder_id = coalesce(i.elder_id, rc.elder_id, sc.elder_id, vasr.elder_id, cp.elder_id)
			                    and b.status = 'ACTIVE'
			                    and (b.expires_at is null or b.expires_at > :now))))
			""";

	private static final String NEWEST_FIRST = " order by n.created_at desc, n.id desc limit :size offset :offset";
	private static final String ONE = " and n.id = :id";

	private static final String PAGE_OWN = COLUMNS + FROM + NEWEST_FIRST;
	private static final String PAGE_FAMILY = COLUMNS + FROM + FAMILY_SCOPE + NEWEST_FIRST;
	private static final String COUNT_OWN = "select count(*) " + FROM;
	private static final String COUNT_FAMILY = "select count(*) " + FROM + FAMILY_SCOPE;
	private static final String FIND_OWN = COLUMNS + FROM + ONE;
	private static final String FIND_FAMILY = COLUMNS + FROM + FAMILY_SCOPE + ONE;

	private static final String DELIVER = """
			update notification set status = 'SENT', sent_at = :now
			where recipient_user_id = :userId and channel = 'IN_APP' and status = 'PENDING'
			""";
	private static final String DELIVER_FAMILY = """
			update notification n
			left join incident i on n.resource_type = 'INCIDENT' and i.id = n.resource_id
			left join roster_change rc on n.resource_type = 'ROSTER_CHANGE' and rc.id = n.resource_id
			left join spot_check sc on n.resource_type = 'SPOT_CHECK' and sc.id = n.resource_id
			left join value_added_service_request vasr on n.resource_type = 'VALUE_ADDED_REQUEST' and vasr.id = n.resource_id
			left join care_plan cp on n.resource_type = 'CARE_PLAN' and cp.id = n.resource_id
			set n.status = 'SENT', n.sent_at = :now
			where n.recipient_user_id = :userId and n.channel = 'IN_APP' and n.status = 'PENDING'
			""" + FAMILY_SCOPE;

	private static final String MARK_READ = """
			update notification set status = 'READ', read_at = :now where id = :id and status = 'SENT'
			""";

	private static final String MARK_ALL_OWN = """
			update notification n set n.status = 'READ', n.read_at = :now
			where n.recipient_user_id = :userId and n.channel = 'IN_APP' and n.status = 'SENT'
			""";

	private static final String MARK_ALL_FAMILY = """
			update notification n
			left join incident i on n.resource_type = 'INCIDENT' and i.id = n.resource_id
			left join roster_change rc on n.resource_type = 'ROSTER_CHANGE' and rc.id = n.resource_id
			left join spot_check sc on n.resource_type = 'SPOT_CHECK' and sc.id = n.resource_id
			left join value_added_service_request vasr on n.resource_type = 'VALUE_ADDED_REQUEST' and vasr.id = n.resource_id
			left join care_plan cp on n.resource_type = 'CARE_PLAN' and cp.id = n.resource_id
			set n.status = 'READ', n.read_at = :now
			where n.recipient_user_id = :userId and n.channel = 'IN_APP' and n.status = 'SENT'
			""" + FAMILY_SCOPE;

	private static final List<String> DISPLAYABLE = Notification.DISPLAYABLE.stream().map(Enum::name).sorted().toList();

	private final JdbcClient jdbc;

	JdbcNotificationInbox(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public int deliverPending(InboxReader reader, LocalDateTime now) {
		return jdbc.sql(reader.family() ? DELIVER_FAMILY : DELIVER)
				.param("userId", reader.userId()).param("now", Timestamp.valueOf(now)).update();
	}

	@Override
	public List<InboxItem> page(InboxReader reader, Notification.Status status, int page, int size, LocalDateTime now) {
		return jdbc.sql(reader.family() ? PAGE_FAMILY : PAGE_OWN)
				.param("userId", reader.userId())
				.param("statuses", statuses(status))
				.param("now", Timestamp.valueOf(now))
				.param("size", size)
				.param("offset", (long) page * size)
				.query(JdbcNotificationInbox::item)
				.list();
	}

	@Override
	public long count(InboxReader reader, Notification.Status status, LocalDateTime now) {
		Long count = jdbc.sql(reader.family() ? COUNT_FAMILY : COUNT_OWN)
				.param("userId", reader.userId())
				.param("statuses", statuses(status))
				.param("now", Timestamp.valueOf(now))
				.query(Long.class)
				.single();
		return count == null ? 0 : count;
	}

	@Override
	public Optional<InboxItem> find(InboxReader reader, Long id, LocalDateTime now) {
		return jdbc.sql(reader.family() ? FIND_FAMILY : FIND_OWN)
				.param("userId", reader.userId())
				.param("statuses", DISPLAYABLE)
				.param("now", Timestamp.valueOf(now))
				.param("id", id)
				.query(JdbcNotificationInbox::item)
				.optional();
	}

	@Override
	public void markRead(Long id, LocalDateTime now) {
		jdbc.sql(MARK_READ).param("id", id).param("now", Timestamp.valueOf(now)).update();
	}

	@Override
	public int markAllRead(InboxReader reader, LocalDateTime now) {
		return jdbc.sql(reader.family() ? MARK_ALL_FAMILY : MARK_ALL_OWN)
				.param("userId", reader.userId())
				.param("now", Timestamp.valueOf(now))
				.update();
	}

	private static List<String> statuses(Notification.Status status) {
		return status == null ? DISPLAYABLE : List.of(status.name());
	}

	private static InboxItem item(ResultSet rs, int row) throws SQLException {
		Notification notification = new Notification(rs.getLong("id"), rs.getLong("recipient_user_id"),
				rs.getString("event_type"), Notification.Channel.valueOf(rs.getString("channel")), rs.getString("title"),
				rs.getString("body"), rs.getString("resource_type"), nullableLong(rs, "resource_id"),
				Notification.Status.valueOf(rs.getString("status")), time(rs, "created_at"), time(rs, "sent_at"),
				time(rs, "read_at"));
		return new InboxItem(notification, nullableLong(rs, "elder_id"));
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
		Timestamp at = rs.getTimestamp(column);
		return at == null ? null : at.toLocalDateTime();
	}
}
