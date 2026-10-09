package sg.nus.carelink.notification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.notification.domain.model.NotificationSubscription;
import sg.nus.carelink.notification.infrastructure.persistence.entity.NotificationSubscriptionJpaEntity;
import sg.nus.carelink.notification.infrastructure.persistence.repository.NotificationSubscriptionJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class NotificationSubscriptionRepositoryAdapterTest {

	private final NotificationSubscriptionJpaRepository jpa = mock(NotificationSubscriptionJpaRepository.class);
	private final NotificationSubscriptionRepositoryAdapter adapter = new NotificationSubscriptionRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		NotificationSubscriptionJpaEntity entity = new NotificationSubscriptionJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<NotificationSubscription> found = adapter.findById(7L);

		assertThat(found).isPresent();
		assertThat(found.get().id()).isEqualTo(7L);
	}

	@Test
	void findByIdIsEmptyWhenThereIsNoRow() {
		when(jpa.findById(any())).thenReturn(Optional.empty());

		assertThat(adapter.findById(7L)).isEmpty();
	}

	@Test
	void saveGoesThroughSpringDataAndComesBackAsDomain() {
		NotificationSubscriptionJpaEntity entity = new NotificationSubscriptionJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(NotificationSubscriptionJpaEntity.class))).thenReturn(entity);

		NotificationSubscription saved = adapter.save(NotificationSubscriptionMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
