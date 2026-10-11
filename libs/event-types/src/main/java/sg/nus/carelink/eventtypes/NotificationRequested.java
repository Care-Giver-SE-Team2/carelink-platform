package sg.nus.carelink.eventtypes;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * A message should reach one person. Published by the module that has something to say, in the
 * transaction that decided it, one event per recipient; handled by notification, which keeps the
 * message for the recipient's inbox.
 *
 * @param kind what happened, such as {@code CREDENTIAL_EXPIRED}; the inbox shows it as the message's event type
 * @param channel {@code IN_APP}, {@code SMS} or {@code EMAIL}
 * @param resourceType what the message links to, such as {@code INCIDENT}; null for an account-only message
 * @param elderId the elder the message is about, when it is about one; the inbox's family rule needs it
 */
public record NotificationRequested(Long recipientUserId, String kind, String channel, String title, String body,
		String resourceType, Long resourceId, Long elderId, OffsetDateTime requestedAt) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "NotificationRequested";

	/** The longest title the inbox keeps. */
	public static final int TITLE_LENGTH = 150;

	/** The longest body the inbox keeps. */
	public static final int BODY_LENGTH = 1000;

	/**
	 * An in-app message, its title and body cut to what the inbox keeps, as the inbox showed them
	 * when the modules wrote it themselves.
	 *
	 * @param requestedAt the sender's wall clock in Singapore
	 */
	public static NotificationRequested inApp(Long recipientUserId, String kind, String title, String body,
			String resourceType, Long resourceId, Long elderId, LocalDateTime requestedAt) {
		return new NotificationRequested(recipientUserId, kind, "IN_APP", cut(title, TITLE_LENGTH),
				cut(body, BODY_LENGTH), resourceType, resourceId, elderId, SingaporeTime.of(requestedAt));
	}

	private static String cut(String text, int max) {
		return text == null || text.length() <= max ? text : text.substring(0, max - 3) + "...";
	}

}
