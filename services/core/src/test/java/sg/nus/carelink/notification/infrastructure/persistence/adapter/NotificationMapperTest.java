package sg.nus.carelink.notification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.notification.domain.model.Notification;
import sg.nus.carelink.notification.infrastructure.persistence.entity.NotificationJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class NotificationMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		NotificationJpaEntity entity = new NotificationJpaEntity();
		entity.setId(1L);
		entity.setRecipientUserId(2L);
		entity.setEventType("v3");
		entity.setChannel(NotificationJpaEntity.Channel.IN_APP);
		entity.setTitle("v5");
		entity.setBody("v6");
		entity.setResourceType("v7");
		entity.setResourceId(8L);
		entity.setStatus(NotificationJpaEntity.Status.PENDING);
		entity.setSentAt(LocalDateTime.of(2026, 9, 6, 10, 11));
		entity.setReadAt(LocalDateTime.of(2026, 9, 6, 10, 12));

		Notification domain = NotificationMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.recipientUserId()).isEqualTo(entity.getRecipientUserId());
		assertThat(domain.eventType()).isEqualTo(entity.getEventType());
		assertThat(domain.channel().name()).isEqualTo(entity.getChannel().name());
		assertThat(domain.title()).isEqualTo(entity.getTitle());
		assertThat(domain.body()).isEqualTo(entity.getBody());
		assertThat(domain.resourceType()).isEqualTo(entity.getResourceType());
		assertThat(domain.resourceId()).isEqualTo(entity.getResourceId());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());
		assertThat(domain.sentAt()).isEqualTo(entity.getSentAt());
		assertThat(domain.readAt()).isEqualTo(entity.getReadAt());

		NotificationJpaEntity back = NotificationMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getRecipientUserId()).isEqualTo(entity.getRecipientUserId());
		assertThat(back.getEventType()).isEqualTo(entity.getEventType());
		assertThat(back.getChannel()).isEqualTo(entity.getChannel());
		assertThat(back.getTitle()).isEqualTo(entity.getTitle());
		assertThat(back.getBody()).isEqualTo(entity.getBody());
		assertThat(back.getResourceType()).isEqualTo(entity.getResourceType());
		assertThat(back.getResourceId()).isEqualTo(entity.getResourceId());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
		assertThat(back.getSentAt()).isEqualTo(entity.getSentAt());
		assertThat(back.getReadAt()).isEqualTo(entity.getReadAt());
	}
}
