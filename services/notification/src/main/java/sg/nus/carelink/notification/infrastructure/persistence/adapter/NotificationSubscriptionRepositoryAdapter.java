package sg.nus.carelink.notification.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.notification.domain.model.NotificationSubscription;
import sg.nus.carelink.notification.domain.repository.NotificationSubscriptionRepository;
import sg.nus.carelink.notification.infrastructure.persistence.repository.NotificationSubscriptionJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class NotificationSubscriptionRepositoryAdapter implements NotificationSubscriptionRepository {

	private final NotificationSubscriptionJpaRepository jpa;

	NotificationSubscriptionRepositoryAdapter(NotificationSubscriptionJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<NotificationSubscription> findById(Long id) {
		return jpa.findById(id).map(NotificationSubscriptionMapper::toDomain);
	}

	@Override
	public NotificationSubscription save(NotificationSubscription notificationSubscription) {
		return NotificationSubscriptionMapper.toDomain(jpa.save(NotificationSubscriptionMapper.toEntity(notificationSubscription)));
	}
}
