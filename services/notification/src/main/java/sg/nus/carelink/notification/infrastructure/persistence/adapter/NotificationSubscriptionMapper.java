package sg.nus.carelink.notification.infrastructure.persistence.adapter;

import sg.nus.carelink.notification.domain.model.NotificationSubscription;
import sg.nus.carelink.notification.infrastructure.persistence.entity.NotificationSubscriptionJpaEntity;

/**
 * JPA entity <-> domain model for notification_subscription, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by NotificationSubscriptionMapperTest.
 */
final class NotificationSubscriptionMapper {

	private NotificationSubscriptionMapper() {
	}

	static NotificationSubscription toDomain(NotificationSubscriptionJpaEntity e) {
		return new NotificationSubscription(
				e.getId(),
				e.getUserId(),
				e.getElderId(),
				e.getEventType(),
				e.getChannel() == null ? null : NotificationSubscription.Channel.valueOf(e.getChannel().name()),
				e.isEnabled(),
				e.getCreatedAt());
	}

	static NotificationSubscriptionJpaEntity toEntity(NotificationSubscription d) {
		NotificationSubscriptionJpaEntity e = new NotificationSubscriptionJpaEntity();
		e.setId(d.id());
		e.setUserId(d.userId());
		e.setElderId(d.elderId());
		e.setEventType(d.eventType());
		e.setChannel(d.channel() == null ? null : NotificationSubscriptionJpaEntity.Channel.valueOf(d.channel().name()));
		e.setEnabled(d.enabled());
		return e;
	}
}
