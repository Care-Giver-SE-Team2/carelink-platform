package sg.nus.carelink.report.domain.repository;

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

/**
 * Port for report's own record of what visit and core tell it: the facts a report is made from
 * ({@link ReportFactsSource} reads them back). Each method takes the event as published, so the
 * record is a projection of the events and holds no rule of its own.
 *
 * <p>Events arrive at least once and in no particular order. Keeping one is therefore idempotent,
 * and an update older than what is kept is ignored, by the field the event catalogue orders it by:
 * the visit's version, the timeline entry's id, or the envelope's sequence. An update that arrives
 * before the record it updates is kept too; what only the creating event carries is filled in when
 * that event arrives.
 */
public interface KeptFacts {

	void keep(VisitScheduled event);

	void keep(VisitCaregiverChanged event);

	void keep(VisitCancelled event);

	void keep(VisitCheckedIn event);

	void keep(VisitCompleted event);

	void keep(VisitExceptionRaised event);

	void keep(VisitClosed event);

	void keep(sg.nus.carelink.eventtypes.VisitEvidenceUpdated event);

	/** @param sequence the envelope's sequence: a task has no version of its own */
	void keep(VisitTaskRecorded event, long sequence);

	void keep(VitalsRecorded event);

	void keep(ElderConfirmationSubmitted event);

	void keep(IncidentRaised event);

	void keep(IncidentUpdated event);

	/** @param sequence the envelope's sequence */
	void keep(SpotCheckUpdated event, long sequence);

	/** @param sequence the envelope's sequence */
	void keep(RosterChangeUpdated event, long sequence);

}
