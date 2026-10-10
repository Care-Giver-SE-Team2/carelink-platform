package sg.nus.carelink.notification.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import sg.nus.carelink.notification.domain.model.Notification;
import sg.nus.carelink.notification.domain.repository.NotificationRepository;

/** Test double for the port: the service is exercised without Spring or a database (as in identity). */
class InMemoryNotificationRepository implements NotificationRepository {

	private final Map<Long, Notification> rows = new HashMap<>();
	private long nextId = 1;

	@Override
	public Optional<Notification> findById(Long id) {
		return Optional.ofNullable(rows.get(id));
	}

	@Override
	public Notification save(Notification notification) {
		Notification stored = notification.id() == null
				? new Notification(nextId, notification.recipientUserId(), notification.eventType(), notification.channel(), notification.title(), notification.body(), notification.resourceType(), notification.resourceId(), notification.status(), notification.createdAt(), notification.sentAt(), notification.readAt())
				: notification;
		rows.put(stored.id(), stored);
		if (notification.id() == null) {
			nextId++;
		}
		return stored;
	}
}
