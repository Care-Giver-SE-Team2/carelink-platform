package sg.nus.carelink.notification.domain.repository;

import java.util.Optional;

import sg.nus.carelink.notification.domain.model.Notification;

/**
 * Port for notification: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.NotificationRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface NotificationRepository {

	Optional<Notification> findById(Long id);

	Notification save(Notification notification);
}
