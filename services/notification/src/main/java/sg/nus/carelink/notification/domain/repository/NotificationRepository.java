package sg.nus.carelink.notification.domain.repository;

import java.util.Optional;

import sg.nus.carelink.notification.domain.model.Notification;

/**
 * Port for notification: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.NotificationRepositoryAdapter. The inbox,
 * which reads across other modules' tables, has its own port, NotificationInbox.
 */
public interface NotificationRepository {

	Optional<Notification> findById(Long id);

	Notification save(Notification notification);
}
