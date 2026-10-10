package sg.nus.carelink.notification.infrastructure.persistence.adapter;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.notification.domain.model.Notification;
import sg.nus.carelink.notification.domain.repository.NotificationRepository;
import sg.nus.carelink.notification.infrastructure.persistence.repository.NotificationJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class NotificationRepositoryAdapter implements NotificationRepository {

	private final NotificationJpaRepository jpa;

	NotificationRepositoryAdapter(NotificationJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<Notification> findById(Long id) {
		return jpa.findById(id).map(NotificationMapper::toDomain);
	}

	@Override
	public Notification save(Notification notification) {
		return NotificationMapper.toDomain(jpa.save(NotificationMapper.toEntity(notification)));
	}
}
