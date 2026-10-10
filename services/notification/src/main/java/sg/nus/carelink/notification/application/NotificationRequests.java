package sg.nus.carelink.notification.application;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.notification.domain.model.Notification;
import sg.nus.carelink.notification.domain.repository.NotificationRepository;

/**
 * Keeps a message another module asked for, for its recipient's inbox. The modules ask through
 * the {@code NotificationRequested} event instead of writing into this module's table; each one
 * becomes a PENDING row, delivered when the recipient's client next asks (see
 * {@link NotificationService}).
 */
@Service
@Transactional
public class NotificationRequests {

	private final NotificationRepository notifications;

	public NotificationRequests(NotificationRepository notifications) {
		this.notifications = notifications;
	}

	public Notification keep(Long recipientUserId, String kind, Notification.Channel channel, String title, String body,
			String resourceType, Long resourceId, LocalDateTime requestedAt) {
		return notifications.save(Notification.requested(recipientUserId, kind, channel, title, body, resourceType,
				resourceId, requestedAt));
	}

}
