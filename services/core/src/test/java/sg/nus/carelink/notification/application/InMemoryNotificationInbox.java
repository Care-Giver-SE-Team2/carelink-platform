package sg.nus.carelink.notification.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import sg.nus.carelink.notification.domain.model.InboxItem;
import sg.nus.carelink.notification.domain.model.InboxReader;
import sg.nus.carelink.notification.domain.model.Notification;
import sg.nus.carelink.notification.domain.repository.NotificationInbox;

/**
 * The inbox port in memory, with the family rule as the SQL states it: a message about an elder
 * only while bound to that elder, a message about no elder always. The real query is checked
 * against MySQL by NotificationInboxIT.
 */
class InMemoryNotificationInbox implements NotificationInbox {

	private final Map<Long, InboxItem> rows = new ConcurrentHashMap<>();
	private final Set<String> bindings = new HashSet<>();
	private long nextId = 1;

	long add(Long recipient, String title, Notification.Status status, LocalDateTime at, Long elderId) {
		long id = nextId++;
		rows.put(id, new InboxItem(new Notification(id, recipient, "ROSTER_CHANGE_SETTLED", Notification.Channel.IN_APP,
				title, "body", elderId == null ? null : "ROSTER_CHANGE", elderId == null ? null : 40L, status, at,
				status == Notification.Status.PENDING ? null : at, status == Notification.Status.READ ? at : null),
				elderId));
		return id;
	}

	void bind(Long userId, Long elderId) {
		bindings.add(userId + ":" + elderId);
	}

	void unbind(Long userId, Long elderId) {
		bindings.remove(userId + ":" + elderId);
	}

	Notification.Status statusOf(long id) {
		return rows.get(id).notification().status();
	}

	@Override
	public int deliverPending(InboxReader reader, LocalDateTime now) {
		List<InboxItem> pending = rows.values().stream()
				.filter(item -> item.notification().recipientUserId().equals(reader.userId()))
				.filter(item -> item.notification().status() == Notification.Status.PENDING)
				.filter(item -> !reader.family() || item.elderId() == null
						|| bindings.contains(reader.userId() + ":" + item.elderId()))
				.toList();
		pending.forEach(item -> rows.put(item.notification().id(), with(item, Notification.Status.SENT, now, null)));
		return pending.size();
	}

	@Override
	public List<InboxItem> page(InboxReader reader, Notification.Status status, int page, int size, LocalDateTime now) {
		return visible(reader, status).stream()
				.sorted(Comparator.comparing((InboxItem item) -> item.notification().createdAt())
						.thenComparing(item -> item.notification().id()).reversed())
				.skip((long) page * size)
				.limit(size)
				.toList();
	}

	@Override
	public long count(InboxReader reader, Notification.Status status, LocalDateTime now) {
		return visible(reader, status).size();
	}

	@Override
	public Optional<InboxItem> find(InboxReader reader, Long id, LocalDateTime now) {
		return visible(reader, null).stream().filter(item -> item.notification().id().equals(id)).findFirst();
	}

	@Override
	public void markRead(Long id, LocalDateTime now) {
		InboxItem item = rows.get(id);
		if (item != null && item.notification().status() == Notification.Status.SENT) {
			rows.put(id, with(item, Notification.Status.READ, item.notification().sentAt(), now));
		}
	}

	@Override
	public int markAllRead(InboxReader reader, LocalDateTime now) {
		List<InboxItem> unread = visible(reader, Notification.Status.SENT);
		unread.forEach(item -> markRead(item.notification().id(), now));
		return unread.size();
	}

	private List<InboxItem> visible(InboxReader reader, Notification.Status status) {
		List<InboxItem> mine = new ArrayList<>();
		for (InboxItem item : rows.values()) {
			Notification n = item.notification();
			boolean statusFits = status == null ? Notification.DISPLAYABLE.contains(n.status()) : n.status() == status;
			boolean bound = !reader.family() || item.elderId() == null
					|| bindings.contains(reader.userId() + ":" + item.elderId());
			if (n.recipientUserId().equals(reader.userId()) && statusFits && bound) {
				mine.add(item);
			}
		}
		return mine;
	}

	private static InboxItem with(InboxItem item, Notification.Status status, LocalDateTime sentAt,
			LocalDateTime readAt) {
		Notification n = item.notification();
		return new InboxItem(new Notification(n.id(), n.recipientUserId(), n.eventType(), n.channel(), n.title(), n.body(),
				n.resourceType(), n.resourceId(), status, n.createdAt(), sentAt, readAt), item.elderId());
	}
}
