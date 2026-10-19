package sg.nus.carelink.visit.application;

import sg.nus.carelink.visit.domain.model.ElderConfirmation;
import sg.nus.carelink.visit.domain.model.HealthRecord;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.model.VisitTask;

/**
 * What visit tells the other services, as the events in docs/platform/event-catalogue.md. Each call
 * belongs to the transaction of the change it reports, so the event goes out when, and only when, the
 * change commits. The visit passed in is the one just saved, with its new version: one change to a
 * visit is one event, and a consumer keeps the state of the highest version it has seen.
 */
public interface VisitEvents {

	/** @param origin {@code PLAN}, {@code STANDALONE} or {@code MOVED} */
	void scheduled(Visit visit, String origin, Long movedFromVisitId);

	void caregiverChanged(Visit visit, Long previousCaregiverId);

	/** @param reason {@code PLAN_CHANGED}, {@code ABSENCE} or {@code CALLED_OFF} */
	void cancelled(Visit visit, String reason, String note);

	void checkedIn(Visit visit, boolean resumedAfterMissedCheckIn);

	void completed(Visit visit);

	/** The visit became VERIFIED (the elder confirmed it) or AUTO_CLOSED. */
	void closed(Visit visit);

	/** @param cause {@code CAREGIVER_REPORT}, {@code UNCOVERED_AT_START} or {@code ABSENCE_UNCOVERED} */
	void exceptionRaised(Visit visit, String cause, Long incidentId);

	void taskRecorded(VisitTask task, Visit visit);

	void vitalsRecorded(HealthRecord record, Visit visit);

	void elderConfirmationSubmitted(ElderConfirmation confirmation, Visit visit, Long incidentId);
}
