package sg.nus.carelink.report.messaging;

import java.util.function.BiConsumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import sg.nus.carelink.events.EventHandler;
import sg.nus.carelink.events.EventMetadata;
import sg.nus.carelink.eventtypes.ElderConfirmationSubmitted;
import sg.nus.carelink.eventtypes.IncidentRaised;
import sg.nus.carelink.eventtypes.IncidentUpdated;
import sg.nus.carelink.eventtypes.RosterChangeUpdated;
import sg.nus.carelink.eventtypes.SpotCheckUpdated;
import sg.nus.carelink.eventtypes.VisitCancelled;
import sg.nus.carelink.eventtypes.VisitCaregiverChanged;
import sg.nus.carelink.eventtypes.VisitCheckedIn;
import sg.nus.carelink.eventtypes.VisitClosed;
import sg.nus.carelink.eventtypes.VisitCompleted;
import sg.nus.carelink.eventtypes.VisitExceptionRaised;
import sg.nus.carelink.eventtypes.VisitScheduled;
import sg.nus.carelink.eventtypes.VisitTaskRecorded;
import sg.nus.carelink.eventtypes.VitalsRecorded;
import sg.nus.carelink.report.domain.repository.KeptFacts;

/**
 * Takes the facts a report is made from off report's queue: visit's eleven events and core's four
 * (docs/platform/event-catalogue.md, section 3). Each handler is one call to {@link KeptFacts}:
 * keeping a fact is a projection with no rule of its own, so there is no application service in
 * between, as there is for a message ({@code NotificationRequestedHandler} in notification). The
 * library runs each in a transaction with the record that the event was handled.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!worker")
class ReportFactHandlers {
    @Bean
    EventHandler<sg.nus.carelink.eventtypes.VisitEvidenceUpdated> evidenceUpdatedHandler(KeptFacts facts) {
        return on(sg.nus.carelink.eventtypes.VisitEvidenceUpdated.TYPE,
                sg.nus.carelink.eventtypes.VisitEvidenceUpdated.class, (event, sequence) -> facts.keep(event));
    }


	@Bean
	EventHandler<VisitScheduled> visitScheduledHandler(KeptFacts facts) {
		return on(VisitScheduled.TYPE, VisitScheduled.class, (event, sequence) -> facts.keep(event));
	}

	@Bean
	EventHandler<VisitCaregiverChanged> visitCaregiverChangedHandler(KeptFacts facts) {
		return on(VisitCaregiverChanged.TYPE, VisitCaregiverChanged.class, (event, sequence) -> facts.keep(event));
	}

	@Bean
	EventHandler<VisitCancelled> visitCancelledHandler(KeptFacts facts) {
		return on(VisitCancelled.TYPE, VisitCancelled.class, (event, sequence) -> facts.keep(event));
	}

	@Bean
	EventHandler<VisitCheckedIn> visitCheckedInHandler(KeptFacts facts) {
		return on(VisitCheckedIn.TYPE, VisitCheckedIn.class, (event, sequence) -> facts.keep(event));
	}

	@Bean
	EventHandler<VisitCompleted> visitCompletedHandler(KeptFacts facts) {
		return on(VisitCompleted.TYPE, VisitCompleted.class, (event, sequence) -> facts.keep(event));
	}

	@Bean
	EventHandler<VisitExceptionRaised> visitExceptionRaisedHandler(KeptFacts facts) {
		return on(VisitExceptionRaised.TYPE, VisitExceptionRaised.class, (event, sequence) -> facts.keep(event));
	}

	@Bean
	EventHandler<VisitClosed> visitClosedHandler(KeptFacts facts) {
		return on(VisitClosed.TYPE, VisitClosed.class, (event, sequence) -> facts.keep(event));
	}

	@Bean
	EventHandler<VisitTaskRecorded> visitTaskRecordedHandler(KeptFacts facts) {
		return on(VisitTaskRecorded.TYPE, VisitTaskRecorded.class, facts::keep);
	}

	@Bean
	EventHandler<VitalsRecorded> vitalsRecordedHandler(KeptFacts facts) {
		return on(VitalsRecorded.TYPE, VitalsRecorded.class, (event, sequence) -> facts.keep(event));
	}

	@Bean
	EventHandler<ElderConfirmationSubmitted> elderConfirmationSubmittedHandler(KeptFacts facts) {
		return on(ElderConfirmationSubmitted.TYPE, ElderConfirmationSubmitted.class,
				(event, sequence) -> facts.keep(event));
	}

	@Bean
	EventHandler<IncidentRaised> incidentRaisedHandler(KeptFacts facts) {
		return on(IncidentRaised.TYPE, IncidentRaised.class, (event, sequence) -> facts.keep(event));
	}

	@Bean
	EventHandler<IncidentUpdated> incidentUpdatedHandler(KeptFacts facts) {
		return on(IncidentUpdated.TYPE, IncidentUpdated.class, (event, sequence) -> facts.keep(event));
	}

	@Bean
	EventHandler<SpotCheckUpdated> spotCheckUpdatedHandler(KeptFacts facts) {
		return on(SpotCheckUpdated.TYPE, SpotCheckUpdated.class, facts::keep);
	}

	@Bean
	EventHandler<RosterChangeUpdated> rosterChangeUpdatedHandler(KeptFacts facts) {
		return on(RosterChangeUpdated.TYPE, RosterChangeUpdated.class, facts::keep);
	}

	/**
	 * A handler for one type, given the event and its sequence. A message that came through no outbox
	 * has none, and counts as older than anything kept.
	 */
	static <T> EventHandler<T> on(String type, Class<T> payloadType, BiConsumer<T, Long> keep) {
		return new EventHandler<>() {

			@Override
			public String type() {
				return type;
			}

			@Override
			public Class<T> payloadType() {
				return payloadType;
			}

			@Override
			public void handle(T payload, EventMetadata metadata) {
				keep.accept(payload, metadata.sequence() == null ? 0L : metadata.sequence());
			}

		};
	}

}
