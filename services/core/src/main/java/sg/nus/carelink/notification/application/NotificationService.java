package sg.nus.carelink.notification.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.notification.domain.model.Notification;
import sg.nus.carelink.notification.domain.repository.NotificationRepository;

/**
 * Application layer of the notification module (notification subscriptions and delivered notifications).
 *
 * <p>One public method per use case (UC-FM03, UC-FM05): it loads what it needs through
 * the domain ports, calls the domain model, saves, and returns. Business rules stay in
 * domain.model. identity.application.IdentityService is the template.
 */
@Service
@Transactional
public class NotificationService {

	private final NotificationRepository notifications;

	public NotificationService(NotificationRepository notifications) {
		this.notifications = notifications;
	}

	@Transactional(readOnly = true)
	public Optional<Notification> findNotification(Long id) {
		return notifications.findById(id);
	}
}
