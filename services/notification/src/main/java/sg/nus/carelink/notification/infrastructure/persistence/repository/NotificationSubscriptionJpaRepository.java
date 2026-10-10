package sg.nus.carelink.notification.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.notification.infrastructure.persistence.entity.NotificationSubscriptionJpaEntity;

/** Spring Data repository for notification_subscription. Used by persistence.adapter only; never exposed outwards. */
public interface NotificationSubscriptionJpaRepository extends JpaRepository<NotificationSubscriptionJpaEntity, Long> {
}
