package sg.nus.carelink.notification.controller.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import sg.nus.carelink.notification.domain.model.Inbox;
import sg.nus.carelink.notification.domain.model.InboxItem;
import sg.nus.carelink.notification.domain.model.Notification;

/**
 * What the inbox endpoints return, in the shape of the FamilyNotification and
 * FamilyNotificationPage schemas the contract drafted for UC-FM05; every role gets the same
 * shape. Times carry the Singapore offset, as that contract asks.
 */
public final class NotificationResponses {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

	private NotificationResponses() {
	}

	/**
	 * One message. {@code resourceType} and {@code resourceId} say what it is about (an INCIDENT,
	 * ROSTER_CHANGE, SPOT_CHECK or CREDENTIAL), which is how a client links it to a screen; for
	 * an incident, resourceId is the incident's id.
	 */
	public record Item(Long id, Long elderId, String eventType, Notification.Channel channel, String title,
			String body, String resourceType, Long resourceId, Notification.Status status, OffsetDateTime createdAt,
			OffsetDateTime sentAt, OffsetDateTime readAt) {

		public static Item of(InboxItem item) {
			Notification n = item.notification();
			return new Item(n.id(), item.elderId(), n.eventType(), n.channel(), n.title(), n.body(), n.resourceType(),
					n.resourceId(), n.status(), at(n.createdAt()), at(n.sentAt()), at(n.readAt()));
		}
	}

	public record InboxPage(List<Item> items, int page, int size, long totalElements) {

		public static InboxPage of(Inbox inbox) {
			return new InboxPage(inbox.items().stream().map(Item::of).toList(), inbox.page(), inbox.size(),
					inbox.totalElements());
		}
	}

	public record UnreadCount(long unread) {
	}

	public record MarkedRead(int updated) {
	}

	private static OffsetDateTime at(LocalDateTime time) {
		return time == null ? null : time.atZone(SINGAPORE).toOffsetDateTime();
	}
}
