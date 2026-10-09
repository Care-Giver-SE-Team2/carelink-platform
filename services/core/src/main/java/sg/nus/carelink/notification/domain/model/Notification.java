package sg.nus.carelink.notification.domain.model;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * One message for one person, as every module writes it: a row in {@code notification}. The
 * modules that raise events (incidents, roster changes, spot checks, certificates) insert the
 * rows as PENDING; this module delivers the in-app ones and is where their recipient reads them
 * (SUP-02, the in-app channel).
 *
 * <p>An in-app message is PENDING until its recipient's client asks for the inbox, SENT once
 * it is in the inbox, and READ once opened. Only SENT and READ are ever shown. Must not import
 * JPA or Spring Data; ArchUnit rejects the build if it does.
 */
public record Notification(
		Long id,
		Long recipientUserId,
		String eventType,
		Notification.Channel channel,
		String title,
		String body,
		String resourceType,
		Long resourceId,
		Notification.Status status,
		LocalDateTime createdAt,
		LocalDateTime sentAt,
		LocalDateTime readAt) {

	/** The statuses an inbox shows: delivered, opened or not. */
	public static final Set<Status> DISPLAYABLE = Set.of(Status.SENT, Status.READ);

	public enum Channel {
		IN_APP, SMS, EMAIL
	}

	public enum Status {
		PENDING, SENT, READ, FAILED
	}

	/** Delivered to the inbox and not yet opened: what the bell counts. */
	public boolean unread() {
		return status == Status.SENT;
	}
}
