package sg.nus.carelink.notification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.notification.domain.model.Notification;
import sg.nus.carelink.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import sg.nus.carelink.notification.infrastructure.persistence.repository.NotificationJpaRepository;

/** The adapter delegates to Spring Data and maps at the boundary; nothing else. */
class NotificationRepositoryAdapterTest {

	private final NotificationJpaRepository jpa = mock(NotificationJpaRepository.class);
	private final NotificationRepositoryAdapter adapter = new NotificationRepositoryAdapter(jpa);

	@Test
	void findByIdMapsTheEntityToTheDomainModel() {
		NotificationJpaEntity entity = new NotificationJpaEntity();
		entity.setId(7L);
		when(jpa.findById(7L)).thenReturn(Optional.of(entity));

		Optional<Notification> found = adapter.findById(7L);

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
		NotificationJpaEntity entity = new NotificationJpaEntity();
		entity.setId(7L);
		when(jpa.save(any(NotificationJpaEntity.class))).thenReturn(entity);

		Notification saved = adapter.save(NotificationMapper.toDomain(entity));

		assertThat(saved).isNotNull();
	}
}
