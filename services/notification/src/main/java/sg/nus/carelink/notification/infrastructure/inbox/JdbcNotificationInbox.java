package sg.nus.carelink.notification.infrastructure.inbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.notification.domain.model.InboxItem;
import sg.nus.carelink.notification.domain.model.InboxReader;
import sg.nus.carelink.notification.domain.model.Notification;
import sg.nus.carelink.notification.domain.repository.NotificationInbox;

/**
 * The inbox in SQL, on notification's own table only. A message about care carries the elder it is
 * about, as the event that asked for it named the elder, and the family rule takes the elders core
 * says the reader may read now ({@link InboxReader#readableElders()}).
 *
 * <p>Times are bound and read as {@link Timestamp}, the way the messages were written, so the
 * connection's time-zone conversion is the same on both sides.
 */
@Component
class JdbcNotificationInbox implements NotificationInbox {

	private static final String COLUMNS = """
			select n.id, n.recipient_user_id, n.event_type, n.channel, n.title, n.body, n.resource_type,
			       n.resource_id, n.elder_id, n.status, n.created_at, n.sent_at, n.read_at
			""";

	/** The reader's own in-app messages in the asked statuses. */
	private static final String FROM = """
			from notification n
			where n.recipient_user_id = :userId
			  and n.channel = 'IN_APP'
			  and n.status in (:statuses)
			""";

	/**
	 * The family rule. A message about a care record is shown while the reader may read its elder,
	 * and not otherwise; one that names no elder is not shown at all. Only a message with neither
	 * resource type nor resource ID is account-only; unknown resource types must not bypass the
	 * care-content authorization rule. A binding request (FAMILY_BINDING) is shown to the family
	 * member it was sent to whatever its status: it waits on their answer rather than showing care
	 * content, and the reader is already its recipient.
	 */
	private static final String FAMILY_SCOPE = """
			  and ((n.resource_type is null and n.resource_id is null)
			       or n.resource_type = 'FAMILY_BINDING'
			       or (n.resource_type in ('INCIDENT', 'ROSTER_CHANGE', 'SPOT_CHECK', 'VALUE_ADDED_REQUEST', 'CARE_PLAN')
			           and n.elder_id in (:elders)))
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
			update notification n set n.status = 'SENT', n.sent_at = :now
			where n.recipient_user_id = :userId and n.channel = 'IN_APP' and n.status = 'PENDING'
			""";
	private static final String DELIVER_FAMILY = DELIVER + FAMILY_SCOPE;

	private static final String MARK_READ = """
			update notification set status = 'READ', read_at = :now where id = :id and status = 'SENT'
			""";

	private static final String MARK_ALL_OWN = """
			update notification n set n.status = 'READ', n.read_at = :now
			where n.recipient_user_id = :userId and n.channel = 'IN_APP' and n.status = 'SENT'
			""";
	private static final String MARK_ALL_FAMILY = MARK_ALL_OWN + FAMILY_SCOPE;

	/** No elder has this id: an IN list must not be empty, and a reader who may read no elder sees none. */
	private static final List<Long> NO_ELDER = List.of(-1L);

	private static final List<String> DISPLAYABLE = Notification.DISPLAYABLE.stream().map(Enum::name).sorted().toList();

	private final JdbcClient jdbc;

	JdbcNotificationInbox(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public int deliverPending(InboxReader reader, LocalDateTime now) {
		return scoped(reader, DELIVER_FAMILY, DELIVER)
				.param("now", Timestamp.valueOf(now)).update();
	}

	@Override
	public List<InboxItem> page(InboxReader reader, Notification.Status status, int page, int size, LocalDateTime now) {
		return scoped(reader, PAGE_FAMILY, PAGE_OWN)
				.param("statuses", statuses(status))
				.param("size", size)
				.param("offset", (long) page * size)
				.query(JdbcNotificationInbox::item)
				.list();
	}

	@Override
	public long count(InboxReader reader, Notification.Status status, LocalDateTime now) {
		Long count = scoped(reader, COUNT_FAMILY, COUNT_OWN)
				.param("statuses", statuses(status))
				.query(Long.class)
				.single();
		return count == null ? 0 : count;
	}

	@Override
	public Optional<InboxItem> find(InboxReader reader, Long id, LocalDateTime now) {
		return scoped(reader, FIND_FAMILY, FIND_OWN)
				.param("statuses", DISPLAYABLE)
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
		return scoped(reader, MARK_ALL_FAMILY, MARK_ALL_OWN)
				.param("now", Timestamp.valueOf(now))
				.update();
	}

	/** The family statement with the elders the reader may read, or the reader's own. */
	private JdbcClient.StatementSpec scoped(InboxReader reader, String family, String own) {
		if (!reader.family()) {
			return jdbc.sql(own).param("userId", reader.userId());
		}
		Set<Long> elders = reader.readableElders();
		return jdbc.sql(family).param("userId", reader.userId())
				.param("elders", elders.isEmpty() ? NO_ELDER : List.copyOf(elders));
	}

	private static List<String> statuses(Notification.Status status) {
		return status == null ? DISPLAYABLE : List.of(status.name());
	}

	private static InboxItem item(ResultSet rs, int row) throws SQLException {
		Long elderId = nullableLong(rs, "elder_id");
		Notification notification = new Notification(rs.getLong("id"), rs.getLong("recipient_user_id"),
				rs.getString("event_type"), Notification.Channel.valueOf(rs.getString("channel")), rs.getString("title"),
				rs.getString("body"), rs.getString("resource_type"), nullableLong(rs, "resource_id"), elderId,
				Notification.Status.valueOf(rs.getString("status")), time(rs, "created_at"), time(rs, "sent_at"),
				time(rs, "read_at"));
		return new InboxItem(notification, elderId);
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
