package sg.nus.carelink.openplatform.infrastructure.schedule;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sg.nus.carelink.openplatform.application.WebhookDeliveryService;

/**
 * Nothing but a trigger: attempts the webhook deliveries that are due
 * ({@link WebhookDeliveryService#attemptDue}). Every ten seconds by default
 * ({@code carelink.webhooks.poll-interval}), on one replica at a time. A run attempts at most 20
 * deliveries of up to 10 seconds each, well inside the lock's five minutes.
 *
 * <p>{@code carelink.webhooks.scheduler-enabled=false} switches it off, for tests that attempt
 * deliveries themselves.
 */
@Component
@ConditionalOnProperty(name = "carelink.webhooks.scheduler-enabled", havingValue = "true", matchIfMissing = true)
class WebhookDeliveryScheduler {

	private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryScheduler.class);

	private final WebhookDeliveryService deliveries;

	WebhookDeliveryScheduler(WebhookDeliveryService deliveries) {
		this.deliveries = deliveries;
	}

	@SchedulerLock(name = "notification.webhook-deliveries", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
	@Scheduled(fixedDelayString = "${carelink.webhooks.poll-interval:PT10S}",
			initialDelayString = "${carelink.webhooks.poll-initial-delay:PT30S}")
	void attemptDue() {
		try {
			int attempted = deliveries.attemptDue();
			if (attempted > 0) {
				log.debug("Attempted {} webhook deliveries", attempted);
			}
		}
		catch (RuntimeException failure) {
			log.warn("Webhook delivery run failed: {}", failure.getClass().getSimpleName());
		}
	}
}
