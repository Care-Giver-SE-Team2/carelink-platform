package sg.nus.carelink.openplatform.messaging;

import org.springframework.stereotype.Component;

import sg.nus.carelink.events.EventHandler;
import sg.nus.carelink.events.EventMetadata;
import sg.nus.carelink.eventtypes.ProviderJobUpdated;
import sg.nus.carelink.openplatform.application.JobWebhooks;

/**
 * Takes report's {@code ProviderJobUpdated} off notification's queue and hands it to {@link JobWebhooks}:
 * a thin adapter, like {@code NotificationRequestedHandler}. The delivery is made in the transaction
 * that marks the event handled, and attempted later by the webhook scheduler.
 */
@Component
class ProviderJobUpdatedHandler implements EventHandler<ProviderJobUpdated> {

	private final JobWebhooks webhooks;

	ProviderJobUpdatedHandler(JobWebhooks webhooks) {
		this.webhooks = webhooks;
	}

	@Override
	public String type() {
		return ProviderJobUpdated.TYPE;
	}

	@Override
	public Class<ProviderJobUpdated> payloadType() {
		return ProviderJobUpdated.class;
	}

	@Override
	public void handle(ProviderJobUpdated event, EventMetadata metadata) {
		webhooks.jobChanged(event, metadata.id());
	}
}
