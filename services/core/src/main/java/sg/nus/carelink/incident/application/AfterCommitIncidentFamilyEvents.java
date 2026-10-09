package sg.nus.carelink.incident.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sg.nus.carelink.incident.domain.model.FamilyAlertEvent;
import sg.nus.carelink.incident.domain.service.IncidentEventObserver;
import sg.nus.carelink.incident.domain.service.IncidentEventSubject;

/** Transaction timing bridge for shared incident routing and its observers. @author Wang Zhili */
@Component
class AfterCommitIncidentFamilyEvents implements IncidentFamilyEvents {
	private static final Logger log = LoggerFactory.getLogger(AfterCommitIncidentFamilyEvents.class);
	private final IncidentEventSubject subject;

	AfterCommitIncidentFamilyEvents(List<IncidentEventObserver> observers) { subject = new IncidentEventSubject(observers); }

	@Override
	public void raised(UUID eventId, Long incidentId, Long elderId, OffsetDateTime occurredAt) {
		register(new FamilyAlertEvent(eventId, FamilyAlertEvent.Type.INCIDENT_RAISED, incidentId, elderId, occurredAt));
	}

	@Override
	public void unresolved(UUID eventId, Long incidentId, Long elderId, OffsetDateTime occurredAt) {
		register(new FamilyAlertEvent(eventId, FamilyAlertEvent.Type.INCIDENT_UNRESOLVED, incidentId, elderId, occurredAt));
	}

	private void register(FamilyAlertEvent event) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()
				|| !TransactionSynchronizationManager.isSynchronizationActive()
				|| TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
			throw new IllegalStateException("An active write transaction is required to publish an incident event");
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override public void afterCommit() {
				for (var failure : subject.notifyObservers(event)) {
					// No throwable/body: an unavailable outcome store must not expose care contents or undo the source commit.
					log.error("Incident observer failed: eventId={}, incidentId={}, errorType={}",
							event.eventId(), event.incidentId(), failure.getClass().getSimpleName());
				}
			}
		});
	}
}
