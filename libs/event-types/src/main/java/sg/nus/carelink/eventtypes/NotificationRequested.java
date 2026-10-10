package sg.nus.carelink.eventtypes;

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

}
