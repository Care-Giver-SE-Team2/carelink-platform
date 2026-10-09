package sg.nus.carelink.notification.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import sg.nus.carelink.notification.domain.model.InboxItem;
import sg.nus.carelink.notification.domain.model.InboxReader;
import sg.nus.carelink.notification.domain.model.Notification;

/**
 * The in-app inbox in storage terms. Every finder covers the reader's own in-app messages in a
 * displayable status, newest first, and - for a family reader - only those about an elder the
 * reader is bound to at {@code now} (or about no elder at all).
 */
public interface NotificationInbox {

	/** Delivers only currently visible PENDING in-app messages: SENT at {@code now}. */
	int deliverPending(InboxReader reader, LocalDateTime now);

	/** One page; {@code status} null means every displayable status. */
	List<InboxItem> page(InboxReader reader, Notification.Status status, int page, int size, LocalDateTime now);

	/** How many the reader may see in {@code status}; null means every displayable status. */
	long count(InboxReader reader, Notification.Status status, LocalDateTime now);

	/** One message the reader may see. */
	Optional<InboxItem> find(InboxReader reader, Long id, LocalDateTime now);

	/** Opens one SENT message at {@code now}; a message already READ keeps its first time. */
	void markRead(Long id, LocalDateTime now);

	/** Opens every SENT message the reader may see; returns how many. */
	int markAllRead(InboxReader reader, LocalDateTime now);
}
