package sg.nus.carelink.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import sg.nus.carelink.notification.domain.model.Inbox;
import sg.nus.carelink.notification.domain.model.InboxItem;
import sg.nus.carelink.notification.domain.model.InboxReader;
import sg.nus.carelink.notification.domain.model.Notification;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * The inbox rules: your own messages only, delivered when you ask, newest first; a family member's
 * only while bound to the elder they are about, with each read audited; opening one is remembered.
 */
class NotificationServiceTest {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 9, 30);
	private static final Long FIONA = 2L;
	private static final Long ALICE = 11L;
	private static final Long GRACE = 7L;
	private static final Map<String, InboxReader> ACCOUNTS = Map.of("fiona", new InboxReader(FIONA, true), "alice", new InboxReader(ALICE, false));

	private final InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
	private final InMemoryNotificationInbox inbox = new InMemoryNotificationInbox();
	private final List<AccessAuditEntry> audited = new ArrayList<>();
	private final NotificationService service = new NotificationService(repository, inbox,
			(username, family) -> Optional.ofNullable(ACCOUNTS.get(username)), audited::add,
			Clock.fixed(NOW.atZone(SINGAPORE).toInstant(), SINGAPORE));

	@Test
	void findsWhatWasSaved() {
		Notification saved = repository.save(new Notification(null, FIONA, "X", Notification.Channel.IN_APP, "title",
				null, null, null, Notification.Status.PENDING, NOW, null, null));

		assertThat(service.findNotification(saved.id())).contains(saved);
		assertThat(service.findNotification(999L)).isEmpty();
	}

	@Test
	void askingForTheInboxDeliversWhatWasWaitingAndShowsItNewestFirst() {
		inbox.add(FIONA, "older", Notification.Status.READ, NOW.minusDays(1), null);
		long pending = inbox.add(FIONA, "newer", Notification.Status.PENDING, NOW.minusHours(1), null);
		inbox.add(ALICE, "for somebody else", Notification.Status.PENDING, NOW, null);

		Inbox mine = service.inbox("alice", false, null, 0, 20);
		Inbox fionas = service.inbox("fiona", false, null, 0, 20);

		assertThat(mine.items()).extracting(item -> item.notification().title()).containsExactly("for somebody else");
		assertThat(fionas.items()).extracting(item -> item.notification().title()).containsExactly("newer", "older");
		assertThat(fionas.totalElements()).isEqualTo(2);
		assertThat(inbox.statusOf(pending)).isEqualTo(Notification.Status.SENT);
		assertThat(service.unreadCount("fiona", false)).isEqualTo(1);
		assertThat(audited).as("only the current family reader is audited").singleElement()
				.satisfies(entry -> assertThat(entry.actorUserId()).isEqualTo(FIONA));
	}

	@Test
	void aFamilyMemberSeesMessagesAboutAnElderOnlyWhileBoundAndEachReadIsAudited() {
		inbox.bind(FIONA, GRACE);
		inbox.add(FIONA, "about Grace", Notification.Status.SENT, NOW.minusHours(2), GRACE);
		inbox.add(FIONA, "about the account", Notification.Status.SENT, NOW.minusHours(1), null);

		assertThat(service.inbox("fiona", true, null, 0, 20).totalElements()).isEqualTo(2);
		inbox.unbind(FIONA, GRACE);
		Inbox afterRevoking = service.inbox("fiona", true, null, 0, 20);

		assertThat(afterRevoking.items()).extracting(item -> item.notification().title())
				.containsExactly("about the account");
		assertThat(service.unreadCount("fiona", true)).isEqualTo(1);
		assertThat(audited).hasSize(2).allSatisfy(entry -> {
			assertThat(entry.actorUserId()).isEqualTo(FIONA);
			assertThat(entry.resourceType()).isEqualTo("notification_inbox");
			assertThat(entry.outcome()).isEqualTo(AccessAuditEntry.Outcome.OK);
		});
	}

	@Test
	void aPageOutOfRangeIsBroughtBackIntoIt() {
		inbox.add(FIONA, "only", Notification.Status.SENT, NOW, null);

		Inbox outOfRange = service.inbox("fiona", false, null, -3, 5000);

		assertThat(outOfRange.page()).isZero();
		assertThat(outOfRange.size()).isEqualTo(NotificationService.MAX_PAGE_SIZE);
		assertThat(service.inbox("fiona", false, null, 0, 0).size()).isEqualTo(1);
	}

	@Test
	void aStatusNarrowsTheInbox() {
		inbox.add(FIONA, "read", Notification.Status.READ, NOW.minusHours(2), null);
		inbox.add(FIONA, "unread", Notification.Status.SENT, NOW.minusHours(1), null);

		assertThat(service.inbox("fiona", false, Notification.Status.READ, 0, 20).items())
				.extracting(item -> item.notification().title()).containsExactly("read");
	}

	@Test
	void openingAMessageMarksItReadOnceAndKeepsTheFirstTime() {
		long id = inbox.add(FIONA, "Visit moved", Notification.Status.PENDING, NOW.minusHours(1), null);

		InboxItem read = service.markRead(id, "fiona", false);
		InboxItem again = service.markRead(id, "fiona", false);

		assertThat(read.notification().status()).isEqualTo(Notification.Status.READ);
		assertThat(read.notification().readAt()).isEqualTo(NOW);
		assertThat(again).isEqualTo(read);
	}

	@Test
	void openingAMessageAsFamilyIsAudited() {
		long id = inbox.add(FIONA, "account", Notification.Status.SENT, NOW, null);

		service.markRead(id, "fiona", true);

		assertThat(audited).singleElement().satisfies(entry -> {
			assertThat(entry.resourceType()).isEqualTo("notification");
			assertThat(entry.resourceId()).isEqualTo(id);
		});
	}

	@Test
	void somebodyElsesMessageOrOneNoLongerVisibleIsNotFound() {
		long alices = inbox.add(ALICE, "for alice", Notification.Status.SENT, NOW, null);
		long aboutGrace = inbox.add(FIONA, "about Grace", Notification.Status.SENT, NOW, GRACE);

		assertThatThrownBy(() -> service.markRead(alices, "fiona", false)).isInstanceOf(ResourceNotFound.class);
		assertThatThrownBy(() -> service.markRead(aboutGrace, "fiona", true)).isInstanceOf(ResourceNotFound.class);
	}

	@Test
	void anAccountThatDoesNotExistHasNoInbox() {
		assertThatThrownBy(() -> service.unreadCount("nobody", false)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void aSessionPredatingTheFamilyRoleStillUsesCurrentFamilyScope() {
		long id = inbox.add(FIONA, "Unbound elder", Notification.Status.PENDING, NOW, GRACE);
		assertThat(service.inbox("fiona", false, null, 0, 20).items()).isEmpty();
		assertThat(service.unreadCount("fiona", false)).isZero();
		assertThatThrownBy(() -> service.markRead(id, "fiona", false)).isInstanceOf(ResourceNotFound.class);
		assertThat(service.markAllRead("fiona", false)).isZero();
		assertThat(inbox.statusOf(id)).isEqualTo(Notification.Status.PENDING);
		assertThatThrownBy(() -> service.inbox("fiona", false, Notification.Status.PENDING, 0, 20))
				.isInstanceOf(ResponseStatusException.class);
	}

	@Test
	void aUtcClockStillWritesSingaporeDeliveryAndReadTime() {
		var utcService = new NotificationService(repository, inbox,
				(username, family) -> Optional.ofNullable(ACCOUNTS.get(username)), audited::add,
				Clock.fixed(NOW.atZone(SINGAPORE).toInstant(), ZoneId.of("UTC")));
		long id = inbox.add(FIONA, "Account notice", Notification.Status.PENDING, NOW.minusHours(1), null);
		InboxItem item = utcService.markRead(id, "fiona", true);
		assertThat(item.notification().sentAt()).isEqualTo(NOW);
		assertThat(item.notification().readAt()).isEqualTo(NOW);
	}

	@Test
	void markingEverythingReadCountsWhatWasUnread() {
		inbox.add(FIONA, "one", Notification.Status.PENDING, NOW.minusHours(2), null);
		inbox.add(FIONA, "two", Notification.Status.SENT, NOW.minusHours(1), null);
		inbox.add(FIONA, "seen", Notification.Status.READ, NOW.minusDays(1), null);

		assertThat(service.markAllRead("fiona", false)).isEqualTo(2);
		assertThat(service.unreadCount("fiona", false)).isZero();
	}
}
