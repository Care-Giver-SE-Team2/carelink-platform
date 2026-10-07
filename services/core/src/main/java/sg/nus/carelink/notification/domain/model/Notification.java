package sg.nus.carelink.notification.domain.model;

import java.time.LocalDateTime;

/**
 * Domain model for notification.
 *
 * <p>Generated starting point: the same fields as the table, and nothing else. This is
 * where the business rules and the design patterns go — reshape it into a proper
 * aggregate (add behaviour, fold child tables in, drop columns the domain does not
 * care about). identity.domain.model.AppUser is the template. Must not import JPA or
 * Spring Data; ArchUnit rejects the build if it does.
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

	public enum Channel {
		IN_APP, SMS, EMAIL
	}

	public enum Status {
		PENDING, SENT, READ, FAILED
	}
}
