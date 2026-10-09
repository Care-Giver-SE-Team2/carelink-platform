package sg.nus.carelink.notification.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.notification.infrastructure.persistence.entity.NotificationJpaEntity;

/** Spring Data repository for notification. Used by persistence.adapter only; never exposed outwards. */
public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, Long> {
}
