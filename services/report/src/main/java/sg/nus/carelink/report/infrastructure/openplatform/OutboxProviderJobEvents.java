package sg.nus.carelink.report.infrastructure.openplatform;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.ProviderJobUpdated;
import sg.nus.carelink.eventtypes.SingaporeTime;
import sg.nus.carelink.report.application.ProviderJobEvents;
import sg.nus.carelink.report.domain.model.ProviderJob;

/** {@link ProviderJobEvents} as {@code ProviderJobUpdated} (docs/platform/event-catalogue.md), through the outbox. */
@Component
class OutboxProviderJobEvents implements ProviderJobEvents {

	private final Events events;
	private final Clock clock;

	OutboxProviderJobEvents(Events events, Clock clock) {
		this.events = events;
		this.clock = clock;
	}

	@Override
	public void changed(ProviderJob job) {
		events.publish(ProviderJobUpdated.TYPE, of(job, LocalDateTime.now(clock)));
	}

	static ProviderJobUpdated of(ProviderJob job, LocalDateTime at) {
		return new ProviderJobUpdated(job.id(), job.providerId(), job.requestId(), job.status().name(),
				job.serviceName(), job.district(), job.areaName(), SingaporeTime.of(job.scheduledStart()),
				job.durationMinutes(), SingaporeTime.of(job.respondByShown()), SingaporeTime.of(job.dispatchedAt()),
				job.cancelReason(), job.version(), SingaporeTime.of(at));
	}
}
