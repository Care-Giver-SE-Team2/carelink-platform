package sg.nus.carelink.notification.domain.model;

import java.time.LocalDateTime;

/**
 * Domain model for notification_subscription.
 *
 * <p>Generated starting point: the same fields as the table, and nothing else. This is
 * where the business rules and the design patterns go — reshape it into a proper
 * aggregate (add behaviour, fold child tables in, drop columns the domain does not
 * care about). identity.domain.model.AppUser is the template. Must not import JPA or
 * Spring Data; ArchUnit rejects the build if it does.
 */
public record NotificationSubscription(
		Long id,
		Long userId,
		Long elderId,
		String eventType,
		NotificationSubscription.Channel channel,
		boolean enabled,
		LocalDateTime createdAt) {

	public enum Channel {
		IN_APP, SMS, EMAIL
	}
}
