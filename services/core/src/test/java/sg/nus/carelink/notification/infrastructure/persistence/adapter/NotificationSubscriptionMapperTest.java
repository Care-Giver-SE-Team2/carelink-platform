package sg.nus.carelink.notification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.notification.domain.model.NotificationSubscription;
import sg.nus.carelink.notification.infrastructure.persistence.entity.NotificationSubscriptionJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class NotificationSubscriptionMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		NotificationSubscriptionJpaEntity entity = new NotificationSubscriptionJpaEntity();
		entity.setId(1L);
		entity.setUserId(2L);
		entity.setElderId(3L);
		entity.setEventType("v4");
		entity.setChannel(NotificationSubscriptionJpaEntity.Channel.IN_APP);
		entity.setEnabled(true);

		NotificationSubscription domain = NotificationSubscriptionMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.userId()).isEqualTo(entity.getUserId());
		assertThat(domain.elderId()).isEqualTo(entity.getElderId());
		assertThat(domain.eventType()).isEqualTo(entity.getEventType());
		assertThat(domain.channel().name()).isEqualTo(entity.getChannel().name());
		assertThat(domain.enabled()).isEqualTo(entity.isEnabled());

		NotificationSubscriptionJpaEntity back = NotificationSubscriptionMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getUserId()).isEqualTo(entity.getUserId());
		assertThat(back.getElderId()).isEqualTo(entity.getElderId());
		assertThat(back.getEventType()).isEqualTo(entity.getEventType());
		assertThat(back.getChannel()).isEqualTo(entity.getChannel());
		assertThat(back.isEnabled()).isEqualTo(entity.isEnabled());
	}
}
