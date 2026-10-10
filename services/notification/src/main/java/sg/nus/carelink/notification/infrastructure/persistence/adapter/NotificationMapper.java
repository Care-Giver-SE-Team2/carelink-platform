package sg.nus.carelink.notification.infrastructure.persistence.adapter;

import sg.nus.carelink.notification.domain.model.Notification;
import sg.nus.carelink.notification.infrastructure.persistence.entity.NotificationJpaEntity;

/**
 * JPA entity <-> domain model for notification, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by NotificationMapperTest.
 */
final class NotificationMapper {

	private NotificationMapper() {
	}

	static Notification toDomain(NotificationJpaEntity e) {
		return new Notification(
				e.getId(),
				e.getRecipientUserId(),
				e.getEventType(),
				e.getChannel() == null ? null : Notification.Channel.valueOf(e.getChannel().name()),
				e.getTitle(),
				e.getBody(),
				e.getResourceType(),
				e.getResourceId(),
				e.getStatus() == null ? null : Notification.Status.valueOf(e.getStatus().name()),
				e.getCreatedAt(),
				e.getSentAt(),
				e.getReadAt());
	}

	static NotificationJpaEntity toEntity(Notification d) {
		NotificationJpaEntity e = new NotificationJpaEntity();
		e.setId(d.id());
		e.setRecipientUserId(d.recipientUserId());
		e.setEventType(d.eventType());
		e.setChannel(d.channel() == null ? null : NotificationJpaEntity.Channel.valueOf(d.channel().name()));
		e.setTitle(d.title());
		e.setBody(d.body());
		e.setResourceType(d.resourceType());
		e.setResourceId(d.resourceId());
		e.setStatus(d.status() == null ? null : NotificationJpaEntity.Status.valueOf(d.status().name()));
		e.setSentAt(d.sentAt());
		e.setReadAt(d.readAt());
		return e;
	}
}
