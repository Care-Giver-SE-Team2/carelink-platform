package sg.nus.carelink.openplatform.application;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.ProviderUpdated;
import sg.nus.carelink.eventtypes.SingaporeTime;
import sg.nus.carelink.openplatform.domain.model.Provider;

/**
 * Tells report what became of a provider ({@code ProviderUpdated}, docs/platform/event-catalogue.md),
 * in the transaction that changed it, with its whole state after the change.
 */
@Component
class ProviderEvents {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

	private final Events events;

	ProviderEvents(Events events) {
		this.events = events;
	}

	void changed(Provider provider, Instant at) {
		events.publish(ProviderUpdated.TYPE, of(provider, at));
	}

	static ProviderUpdated of(Provider provider, Instant at) {
		return new ProviderUpdated(provider.id(), provider.clientId(), provider.organisationName(),
				provider.status().name(), provider.services(), provider.version(),
				SingaporeTime.of(LocalDateTime.ofInstant(at, SINGAPORE)));
	}
}
