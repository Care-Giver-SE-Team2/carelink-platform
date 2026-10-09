package sg.nus.carelink.notification.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.notification.domain.model.Inbox;
import sg.nus.carelink.notification.domain.model.InboxItem;
import sg.nus.carelink.notification.domain.model.InboxReader;
import sg.nus.carelink.notification.domain.model.Notification;
import sg.nus.carelink.notification.domain.repository.NotificationInbox;
import sg.nus.carelink.notification.domain.repository.NotificationRepository;
import sg.nus.carelink.notification.domain.repository.RecipientDirectory;
import sg.nus.carelink.shared.audit.application.AccessAudit;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * The in-app inbox (SUP-02), to the contract drafted for the family side (UC-FM05): every module
 * writes its messages into {@code notification} as PENDING, and this is where the person they
 * were written for reads them - the bell in each client's header. It is a pull inbox: a message
 * is delivered, PENDING to SENT, when its recipient's client next asks.
 *
 * <p>Whoever is signed in sees their own messages and nobody else's. A family member also sees a
 * message about an elder only while bound to that elder, and their reads go to the access audit
 * like every other family read of care content.
 */
@Service
@Transactional
public class NotificationService {

	/** The contract's page-size ceiling. */
	static final int MAX_PAGE_SIZE = 200;
	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

	private static final String INBOX = "notification_inbox";
	private static final String NOTIFICATION = "notification";

	private final NotificationRepository notifications;
	private final NotificationInbox inbox;
	private final RecipientDirectory recipients;
	private final AccessAudit audit;
	private final Clock clock;

	public NotificationService(NotificationRepository notifications, NotificationInbox inbox,
			RecipientDirectory recipients, AccessAudit audit, Clock clock) {
		this.notifications = notifications;
		this.inbox = inbox;
		this.recipients = recipients;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Optional<Notification> findNotification(Long id) {
		return notifications.findById(id);
	}

	/** My messages, newest first; {@code status} null means SENT and READ both. */
	public Inbox inbox(String username, boolean family, Notification.Status status, int page, int size) {
		InboxReader reader = reader(username, family);
		if (reader.family() && status != null && !Notification.DISPLAYABLE.contains(status)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A family inbox holds SENT and READ messages only");
		}
		LocalDateTime now = now();
		inbox.deliverPending(reader, now);
		int safePage = Math.max(0, page);
		int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
		Inbox mine = new Inbox(inbox.page(reader, status, safePage, safeSize, now), safePage, safeSize,
				inbox.count(reader, status, now));
		if (reader.family()) {
			audit.append(new AccessAuditEntry(reader.userId(), "READ", INBOX, null, AccessAuditEntry.Outcome.OK,
					"page=" + safePage + ";size=" + safeSize));
		}
		return mine;
	}

	/** The number on the bell: delivered and not yet opened. */
	public long unreadCount(String username, boolean family) {
		InboxReader reader = reader(username, family);
		LocalDateTime now = now();
		inbox.deliverPending(reader, now);
		return inbox.count(reader, Notification.Status.SENT, now);
	}

	/**
	 * I open one message. One that is not mine, or that I may no longer see, is not found rather
	 * than forbidden: for me it does not exist. Opening it again keeps the first time.
	 */
	public InboxItem markRead(Long id, String username, boolean family) {
		InboxReader reader = reader(username, family);
		LocalDateTime now = now();
		inbox.deliverPending(reader, now);
		InboxItem item = inbox.find(reader, id, now).orElseThrow(() -> new ResourceNotFound("Notification", id));
		if (item.notification().unread()) {
			inbox.markRead(id, now);
			item = inbox.find(reader, id, now).orElse(item);
		}
		if (reader.family()) {
			audit.append(new AccessAuditEntry(reader.userId(), "READ", NOTIFICATION, id, AccessAuditEntry.Outcome.OK,
					"marked read"));
		}
		return item;
	}

	/** Everything I may see and have not opened is marked read; returns how many. */
	public int markAllRead(String username, boolean family) {
		InboxReader reader = reader(username, family);
		LocalDateTime now = now();
		inbox.deliverPending(reader, now);
		return inbox.markAllRead(reader, now);
	}

	private InboxReader reader(String username, boolean family) {
		return recipients.readerOf(username, family)
				.orElseThrow(() -> new AccessDeniedException("Current account is not authorized to use the inbox"));
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), SINGAPORE);
	}
}
