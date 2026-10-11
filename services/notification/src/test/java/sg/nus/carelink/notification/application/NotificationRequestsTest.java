package sg.nus.carelink.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import sg.nus.carelink.notification.domain.model.Notification;

/** A message another module asked for waits PENDING for its recipient, and needs a recipient, a kind and a title. */
class NotificationRequestsTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 12, 9, 0);

	private final InMemoryNotificationRepository notifications = new InMemoryNotificationRepository();

	private final NotificationRequests requests = new NotificationRequests(notifications);

	@Test
	void aRequestedMessageWaitsPendingForItsRecipient() {
		Notification kept = requests.keep(7L, "CREDENTIAL_EXPIRED", Notification.Channel.IN_APP,
				"Your First aid certificate expired", "Upload the renewed certificate.", "CREDENTIAL", 8732L, null, NINE);

		assertThat(kept.id()).isNotNull();
		assertThat(notifications.findById(kept.id())).contains(kept);
		assertThat(kept.status()).isEqualTo(Notification.Status.PENDING);
		assertThat(kept.createdAt()).isEqualTo(NINE);
		assertThat(kept.sentAt()).isNull();
		assertThat(kept.readAt()).isNull();
	}

	@Test
	void anAccountOnlyMessageLinksToNothing() {
		Notification kept = requests.keep(7L, "WELCOME", Notification.Channel.IN_APP, "Welcome", null, null, null, null,
				NINE);

		assertThat(kept.resourceType()).isNull();
		assertThat(kept.resourceId()).isNull();
		assertThat(kept.elderId()).isNull();
	}

	@Test
	void aMessageAboutCareKeepsItsElder() {
		Notification kept = requests.keep(9L, "SPOT_CHECK_REQUESTED", Notification.Channel.IN_APP, "May a manager watch?",
				null, "SPOT_CHECK", 44L, 101L, NINE);

		assertThat(notifications.findById(kept.id()).orElseThrow().elderId()).isEqualTo(101L);
	}

	@Test
	void aMessageNeedsARecipientAKindAndATitle() {
		assertThatThrownBy(() -> Notification.requested(null, "WELCOME", Notification.Channel.IN_APP, "Welcome", null,
				null, null, null, NINE)).isInstanceOf(NullPointerException.class).hasMessageContaining("recipientUserId");
		assertThatThrownBy(() -> Notification.requested(7L, null, Notification.Channel.IN_APP, "Welcome", null, null,
				null, null, NINE)).isInstanceOf(NullPointerException.class).hasMessageContaining("eventType");
		assertThatThrownBy(() -> Notification.requested(7L, "WELCOME", Notification.Channel.IN_APP, null, null, null,
				null, null, NINE)).isInstanceOf(NullPointerException.class).hasMessageContaining("title");
	}

}
