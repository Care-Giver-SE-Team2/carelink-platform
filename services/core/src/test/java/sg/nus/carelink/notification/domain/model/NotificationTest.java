package sg.nus.carelink.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Which messages an inbox shows, and which of those the bell counts. */
class NotificationTest {

	private static final LocalDateTime SENT = LocalDateTime.of(2026, 10, 7, 9, 0);

	@Test
	void onlyADeliveredMessageNotYetOpenedIsUnread() {
		assertThat(message(Notification.Status.SENT).unread()).isTrue();
		assertThat(message(Notification.Status.PENDING).unread()).isFalse();
		assertThat(message(Notification.Status.READ).unread()).isFalse();
		assertThat(message(Notification.Status.FAILED).unread()).isFalse();
	}

	@Test
	void anInboxShowsDeliveredAndOpenedMessagesOnly() {
		assertThat(Notification.DISPLAYABLE).containsExactlyInAnyOrder(Notification.Status.SENT, Notification.Status.READ);
	}

	@Test
	void anInboxKeepsItsOwnCopyOfThePage() {
		List<InboxItem> page = new ArrayList<>(List.of(new InboxItem(message(Notification.Status.SENT), 7L)));
		Inbox inbox = new Inbox(page, 0, 20, 1);

		page.clear();

		assertThat(inbox.items()).hasSize(1);
		assertThat(inbox.items().get(0).elderId()).isEqualTo(7L);
	}

	@Test
	void aReaderIsAnAccountAndWhetherTheFamilyRuleApplies() {
		InboxReader reader = new InboxReader(2L, true);

		assertThat(reader.userId()).isEqualTo(2L);
		assertThat(reader.family()).isTrue();
	}

	private static Notification message(Notification.Status status) {
		return new Notification(1L, 2L, "SPOT_CHECK_REQUESTED", Notification.Channel.IN_APP, "May a manager watch?",
				"body", "SPOT_CHECK", 9L, status, SENT, status == Notification.Status.PENDING ? null : SENT,
				status == Notification.Status.READ ? SENT : null);
	}
}
