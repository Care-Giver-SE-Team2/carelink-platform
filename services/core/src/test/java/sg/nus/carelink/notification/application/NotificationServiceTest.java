package sg.nus.carelink.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.notification.domain.model.Notification;

class NotificationServiceTest {

	private final InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
	private final NotificationService service = new NotificationService(repository);

	@Test
	void findsWhatWasSaved() {
		Notification saved = repository.save(new Notification(
				null,
				2L,
				"v3",
				Notification.Channel.IN_APP,
				"v5",
				"v6",
				"v7",
				8L,
				Notification.Status.PENDING,
				LocalDateTime.of(2026, 9, 6, 10, 10),
				LocalDateTime.of(2026, 9, 6, 10, 11),
				LocalDateTime.of(2026, 9, 6, 10, 12)));

		assertThat(service.findNotification(saved.id())).contains(saved);
	}

	@Test
	void isEmptyForAnUnknownId() {
		assertThat(service.findNotification(999L)).isEmpty();
	}
}
