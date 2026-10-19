package sg.nus.carelink.visit.application;

import java.util.ArrayList;
import java.util.List;

import sg.nus.carelink.visit.domain.model.ElderConfirmation;
import sg.nus.carelink.visit.domain.model.HealthRecord;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.model.VisitTask;

/**
 * {@link VisitEvents} for unit tests: what visit would publish, in order. {@code why} is the
 * origin, reason or cause the call named, or what else identifies it.
 */
final class RecordingVisitEvents implements VisitEvents {

	record Published(String event, Long visitId, Integer version, Long caregiverId, String why) {
	}

	private final List<Published> published = new ArrayList<>();

	List<Published> published() {
		return List.copyOf(published);
	}

	@Override
	public void scheduled(Visit visit, String origin, Long movedFromVisitId) {
		add("VisitScheduled", visit, movedFromVisitId == null ? origin : origin + " from " + movedFromVisitId);
	}

	@Override
	public void caregiverChanged(Visit visit, Long previousCaregiverId) {
		add("VisitCaregiverChanged", visit, "from " + previousCaregiverId);
	}

	@Override
	public void cancelled(Visit visit, String reason, String note) {
		add("VisitCancelled", visit, note == null ? reason : reason + ": " + note);
	}

	@Override
	public void checkedIn(Visit visit, boolean resumedAfterMissedCheckIn) {
		add("VisitCheckedIn", visit, resumedAfterMissedCheckIn ? "resumed" : "on time");
	}

	@Override
	public void completed(Visit visit) {
		add("VisitCompleted", visit, null);
	}

	@Override
	public void closed(Visit visit) {
		add("VisitClosed", visit, visit.status().name());
	}

	@Override
	public void exceptionRaised(Visit visit, String cause, Long incidentId) {
		add("VisitExceptionRaised", visit, incidentId == null ? cause : cause + " #" + incidentId);
	}

	@Override
	public void taskRecorded(VisitTask task, Visit visit) {
		add("VisitTaskRecorded", visit, task.name() + " " + task.status());
	}

	@Override
	public void vitalsRecorded(HealthRecord record, Visit visit) {
		add("VitalsRecorded", visit, record.readings().size() + " readings");
	}

	@Override
	public void elderConfirmationSubmitted(ElderConfirmation confirmation, Visit visit, Long incidentId) {
		add("ElderConfirmationSubmitted", visit,
				incidentId == null ? confirmation.confirmationStatus().name()
						: confirmation.confirmationStatus() + " #" + incidentId);
	}

	private void add(String event, Visit visit, String why) {
		published.add(new Published(event, visit.id(), visit.version(), visit.caregiverId(), why));
	}
}
