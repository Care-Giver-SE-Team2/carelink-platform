package sg.nus.carelink.visit.infrastructure.events;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.ElderConfirmationSubmitted;
import sg.nus.carelink.eventtypes.SingaporeTime;
import sg.nus.carelink.eventtypes.VisitCancelled;
import sg.nus.carelink.eventtypes.VisitCaregiverChanged;
import sg.nus.carelink.eventtypes.VisitCheckedIn;
import sg.nus.carelink.eventtypes.VisitClosed;
import sg.nus.carelink.eventtypes.VisitCompleted;
import sg.nus.carelink.eventtypes.VisitExceptionRaised;
import sg.nus.carelink.eventtypes.VisitScheduled;
import sg.nus.carelink.eventtypes.VisitTaskRecorded;
import sg.nus.carelink.eventtypes.VitalsRecorded;
import sg.nus.carelink.visit.application.VisitEvents;
import sg.nus.carelink.visit.domain.model.ElderConfirmation;
import sg.nus.carelink.visit.domain.model.HealthRecord;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.model.VisitTask;

/**
 * {@link VisitEvents} in visit's outbox, in the caller's transaction. Times are Singapore wall-clock
 * times with their offset ({@link SingaporeTime}); a change's own time, where the visit keeps none,
 * is the moment it is published.
 */
@Component
class OutboxVisitEvents implements VisitEvents {

	private static final String ABSENCE_UNCOVERED = "ABSENCE_UNCOVERED";

	private final Events events;
	private final Clock clock;

	OutboxVisitEvents(Events events, Clock clock) {
		this.events = events;
		this.clock = clock;
	}

	@Override
	public void scheduled(Visit visit, String origin, Long movedFromVisitId) {
		events.publish(VisitScheduled.TYPE, new VisitScheduled(visit.id(), visit.elderId(), visit.caregiverId(),
				visit.carePlanId(), visit.carePlanNodeId(), visit.serviceType(), time(visit.scheduledStart()),
				time(visit.scheduledEnd()), origin, movedFromVisitId, visit.version()));
	}

	@Override
	public void caregiverChanged(Visit visit, Long previousCaregiverId) {
		events.publish(VisitCaregiverChanged.TYPE, new VisitCaregiverChanged(visit.id(), visit.elderId(),
				time(visit.scheduledStart()), visit.caregiverId(), previousCaregiverId, visit.absenceId(), visit.version(),
				now()));
	}

	@Override
	public void cancelled(Visit visit, String reason, String note) {
		events.publish(VisitCancelled.TYPE, new VisitCancelled(visit.id(), visit.elderId(), time(visit.scheduledStart()),
				visit.caregiverId(), reason, visit.absenceId(), note, visit.version(), now()));
	}

	@Override
	public void checkedIn(Visit visit, boolean resumedAfterMissedCheckIn) {
		events.publish(VisitCheckedIn.TYPE, new VisitCheckedIn(visit.id(), visit.elderId(), time(visit.scheduledStart()),
				visit.caregiverId(), time(visit.checkedInAt()), resumedAfterMissedCheckIn, visit.version()));
	}

	@Override
	public void completed(Visit visit) {
		events.publish(VisitCompleted.TYPE, new VisitCompleted(visit.id(), visit.elderId(), time(visit.scheduledStart()),
				visit.caregiverId(), time(visit.checkedInAt()), time(visit.checkedOutAt()), visit.version()));
	}

	@Override
	public void closed(Visit visit) {
		events.publish(VisitClosed.TYPE, new VisitClosed(visit.id(), visit.elderId(), time(visit.scheduledStart()),
				visit.caregiverId(), visit.status().name(), visit.version(), now()));
	}

	@Override
	public void exceptionRaised(Visit visit, String cause, Long incidentId) {
		events.publish(VisitExceptionRaised.TYPE, new VisitExceptionRaised(visit.id(), visit.elderId(),
				time(visit.scheduledStart()), visit.caregiverId(), cause,
				ABSENCE_UNCOVERED.equals(cause) ? visit.absenceId() : null, incidentId, visit.checkedInAt() != null,
				visit.version()));
	}

	@Override
	public void taskRecorded(VisitTask task, Visit visit) {
		events.publish(VisitTaskRecorded.TYPE, new VisitTaskRecorded(task.id(), visit.id(), visit.elderId(),
				time(visit.scheduledStart()), task.name(), task.status().name(), task.outcome(), task.caregiverNote(),
				time(task.completedAt())));
	}

	/**
	 * The readings as {@code vital_sign} keeps them: two decimals, and an out-of-range flag nothing
	 * sets yet (event catalogue, section 6).
	 */
	@Override
	public void vitalsRecorded(HealthRecord record, Visit visit) {
		events.publish(VitalsRecorded.TYPE, new VitalsRecorded(record.id(), visit.id(), visit.elderId(),
				time(visit.scheduledStart()), time(record.recordedAt()),
				record.healthFlag() == null ? null : record.healthFlag().name(), record.healthNote(),
				record.readings().stream()
						.map(reading -> new VitalsRecorded.Reading(reading.metric(),
								reading.value().setScale(2, RoundingMode.HALF_UP).toPlainString(), reading.unit(), false))
						.toList()));
	}

	@Override
	public void elderConfirmationSubmitted(ElderConfirmation confirmation, Visit visit, Long incidentId) {
		events.publish(ElderConfirmationSubmitted.TYPE, new ElderConfirmationSubmitted(visit.id(), visit.elderId(),
				time(visit.scheduledStart()), confirmation.confirmationStatus().name(),
				confirmation.rating() == null ? null : confirmation.rating().intValue(), confirmation.comment(),
				time(confirmation.confirmedAt()), incidentId));
	}

	private OffsetDateTime now() {
		return SingaporeTime.of(LocalDateTime.now(clock.withZone(SingaporeTime.ZONE)));
	}

	private static OffsetDateTime time(LocalDateTime wallClock) {
		return wallClock == null ? null : SingaporeTime.of(wallClock);
	}
}
