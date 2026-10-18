package sg.nus.carelink.visit.infrastructure.events;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.NotificationRequested;
import sg.nus.carelink.visit.domain.model.MissedCheckInTrigger;
/** The immutable visit-owned trigger and caregiver message commit together. Retrying a failed
 * core lookup rolls back the trigger; core's incident request key makes the next attempt safe. */
@Component
class OutboxCaregiverSystemAlerts {
 private final CoreApi core; private final Events events;
 OutboxCaregiverSystemAlerts(CoreApi core,Events events){this.core=core;this.events=events;}
 @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
 public void triggered(MissedCheckInTrigger fact){
  Long user=core.findCaregiverUserId(fact.caregiverId()).orElse(null);
  if(user==null || core.enabledAccountsWithRole("MANAGER").userIds().contains(user))return;
  events.publish(NotificationRequested.TYPE,NotificationRequested.inApp(user,"INCIDENT_RAISED","Missed check-in alert",
   "Visit %d was not checked in by the allowed threshold. This alert does not block service within the check-in window.".formatted(fact.visitId()),
   "CAREGIVER_SYSTEM_ALERT",fact.incidentId(),null,fact.triggeredAt()));
 }
}
