package sg.nus.carelink.report.messaging;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import sg.nus.carelink.events.EventHandler;
import sg.nus.carelink.events.EventMetadata;
import sg.nus.carelink.eventtypes.ProviderUpdated;
import sg.nus.carelink.report.application.ProviderRemovalService;
import sg.nus.carelink.report.domain.model.Provider;
import sg.nus.carelink.report.domain.repository.Providers;

/**
 * Takes {@code ProviderUpdated} off report's queue and keeps the provider: whom a job may be offered
 * to, and which client its token names. A projection, like the report facts, with no rule of its own;
 * {@link Providers#keep} ignores an event older than the copy held. A provider removed has its open jobs
 * taken back, in the same transaction ({@link ProviderRemovalService}).
 */
@Component
@Profile("!worker")
class ProviderUpdatedHandler implements EventHandler<ProviderUpdated> {

	private final Providers providers;
	private final ProviderRemovalService removals;

	ProviderUpdatedHandler(Providers providers, ProviderRemovalService removals) {
		this.providers = providers;
		this.removals = removals;
	}

	@Override
	public String type() {
		return ProviderUpdated.TYPE;
	}

	@Override
	public Class<ProviderUpdated> payloadType() {
		return ProviderUpdated.class;
	}

	@Override
	public void handle(ProviderUpdated event, EventMetadata metadata) {
		Provider provider = new Provider(event.providerId(), event.clientId(), event.organisationName(),
				Provider.Status.valueOf(event.status()), event.services(), event.version());
		providers.keep(provider);
		if (provider.removed()) {
			removals.takeBackJobsOf(provider.id());
		}
	}
}
