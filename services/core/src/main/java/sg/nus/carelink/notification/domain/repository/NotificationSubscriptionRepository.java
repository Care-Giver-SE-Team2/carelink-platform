package sg.nus.carelink.notification.domain.repository;

import java.util.Optional;

import sg.nus.carelink.notification.domain.model.NotificationSubscription;

/**
 * Port for notification_subscription: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.NotificationSubscriptionRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface NotificationSubscriptionRepository {

	Optional<NotificationSubscription> findById(Long id);

	NotificationSubscription save(NotificationSubscription notificationSubscription);
}
