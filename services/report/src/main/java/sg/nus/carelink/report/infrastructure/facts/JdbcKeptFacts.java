package sg.nus.carelink.report.infrastructure.facts;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.eventtypes.ElderConfirmationSubmitted;
import sg.nus.carelink.eventtypes.IncidentRaised;
import sg.nus.carelink.eventtypes.IncidentUpdated;
import sg.nus.carelink.eventtypes.RosterChangeUpdated;
import sg.nus.carelink.eventtypes.SingaporeTime;
import sg.nus.carelink.eventtypes.SpotCheckUpdated;
import sg.nus.carelink.eventtypes.VisitCancelled;
import sg.nus.carelink.eventtypes.VisitCaregiverChanged;
import sg.nus.carelink.eventtypes.VisitCheckedIn;
import sg.nus.carelink.eventtypes.VisitCompleted;
import sg.nus.carelink.eventtypes.VisitExceptionRaised;
import sg.nus.carelink.eventtypes.VisitScheduled;
import sg.nus.carelink.eventtypes.VisitTaskRecorded;
import sg.nus.carelink.eventtypes.VitalsRecorded;
import sg.nus.carelink.report.domain.repository.KeptFacts;

/**
 * {@link KeptFacts} in report's tables (platform migration V9), one statement per fact: an insert
 * that, when the row is there, updates it only from a newer event. In MySQL the assignments of
 * {@code on duplicate key update} run left to right and each sees the ones before it, so the
 * ordering column is always assigned last; the comparisons before it see the row's own value.
 *
 * <p>Times are written as {@link Timestamp}, the way {@link KeptFactsSource} binds the period it
 * reads them back by (see its class comment).
 */
@Component
class JdbcKeptFacts implements KeptFacts {

	/** A visit's state as its latest event leaves it, kept only from a newer version than the row's. */
	private static final String VISIT_STATE = """
			insert into report_visit (visit_id, elder_id, scheduled_start, status, caregiver_id, checked_in_at,
			                          checked_out_at, version)
			values (:visitId, :elderId, :scheduledStart, :status, :caregiverId, :checkedInAt, :checkedOutAt, :version)
			on duplicate key update
			    status = if(:version > version, :status, status),
			    caregiver_id = if(:version > version, :caregiverId, caregiver_id),
			    checked_in_at = if(:version > version and :setsCheckIn, :checkedInAt, checked_in_at),
			    checked_out_at = if(:version > version and :setsCheckOut, :checkedOutAt, checked_out_at),
			    version = greatest(version, :version)
			""";

	/** What only VisitScheduled carries, and never changes: filled in whenever it arrives. */
	private static final String VISIT_SCHEDULE = """
			update report_visit
			set scheduled_end = :scheduledEnd, service_type = :serviceType, care_plan_id = :carePlanId,
			    care_plan_node_id = :carePlanNodeId
			where visit_id = :visitId
			""";

	private static final String TASK = """
			insert into report_visit_task (task_id, visit_id, elder_id, visit_scheduled_start, name, status,
			                               caregiver_note, recorded_at, sequence)
			values (:taskId, :visitId, :elderId, :visitScheduledStart, :name, :status, :caregiverNote, :recordedAt,
			        :sequence)
			on duplicate key update
			    name = if(:sequence > sequence, :name, name),
			    status = if(:sequence > sequence, :status, status),
			    caregiver_note = if(:sequence > sequence, :caregiverNote, caregiver_note),
			    recorded_at = if(:sequence > sequence, :recordedAt, recorded_at),
			    sequence = greatest(sequence, :sequence)
			""";

	/** A set of readings is never changed: a second delivery adds nothing. */
	private static final String READING = """
			insert into report_vital_sign (health_record_id, visit_id, elder_id, visit_scheduled_start, metric, value,
			                               unit, out_of_range, recorded_at)
			values (:healthRecordId, :visitId, :elderId, :visitScheduledStart, :metric, :value, :unit, :outOfRange,
			        :recordedAt)
			on duplicate key update health_record_id = health_record_id
			""";

	/** A visit takes one answer from its elder. */
	private static final String CONFIRMATION = """
			insert into report_elder_confirmation (visit_id, elder_id, visit_scheduled_start, confirmation_status,
			                                       rating, comment, confirmed_at)
			values (:visitId, :elderId, :visitScheduledStart, :status, :rating, :comment, :confirmedAt)
			on duplicate key update visit_id = visit_id
			""";

	/**
	 * What only IncidentRaised carries; its status and severity only while no timeline entry has
	 * been kept, since an entry is always as new as the raise or newer.
	 */
	private static final String INCIDENT_RAISED = """
			insert into report_incident (incident_id, elder_id, visit_id, category, description, reported_at,
			                             severity, status)
			values (:incidentId, :elderId, :visitId, :category, :description, :reportedAt, :severity, :status)
			on duplicate key update
			    visit_id = :visitId, category = :category, description = :description, reported_at = :reportedAt,
			    severity = if(last_log_id is null, :severity, severity),
			    status = if(last_log_id is null, :status, status)
			""";

	private static final String INCIDENT_LOG = """
			insert into report_incident_log (log_id, incident_id, actor, action, detail, occurred_at)
			values (:logId, :incidentId, :actor, :action, :detail, :occurredAt)
			on duplicate key update log_id = log_id
			""";

	private static final String INCIDENT_STATE = """
			insert into report_incident (incident_id, elder_id, severity, status, resolved_at, last_log_id)
			values (:incidentId, :elderId, :severity, :status, :resolvedAt, :logId)
			on duplicate key update
			    severity = if(last_log_id is null or :logId > last_log_id, :severity, severity),
			    status = if(last_log_id is null or :logId > last_log_id, :status, status),
			    resolved_at = if(last_log_id is null or :logId > last_log_id, :resolvedAt, resolved_at),
			    last_log_id = greatest(coalesce(last_log_id, :logId), :logId)
			""";

	private static final String SPOT_CHECK = """
			insert into report_spot_check (spot_check_id, elder_id, visit_id, caregiver_id, proposed_time,
			                               approval_status, result, outcome, finding, caregiver_response, checked_at,
			                               sequence)
			values (:spotCheckId, :elderId, :visitId, :caregiverId, :proposedTime, :approvalStatus, :result, :outcome,
			        :finding, :caregiverResponse, :checkedAt, :sequence)
			on duplicate key update
			    visit_id = if(:sequence > sequence, :visitId, visit_id),
			    caregiver_id = if(:sequence > sequence, :caregiverId, caregiver_id),
			    proposed_time = if(:sequence > sequence, :proposedTime, proposed_time),
			    approval_status = if(:sequence > sequence, :approvalStatus, approval_status),
			    result = if(:sequence > sequence, :result, result),
			    outcome = if(:sequence > sequence, :outcome, outcome),
			    finding = if(:sequence > sequence, :finding, finding),
			    caregiver_response = if(:sequence > sequence, :caregiverResponse, caregiver_response),
			    checked_at = if(:sequence > sequence, :checkedAt, checked_at),
			    sequence = greatest(sequence, :sequence)
			""";

	private static final String ROSTER_CHANGE = """
			insert into report_roster_change (roster_change_id, visit_id, elder_id, visit_start, original_caregiver_id,
			                                  status, outcome, decided_by, assigned_caregiver_id, sequence)
			values (:rosterChangeId, :visitId, :elderId, :visitStart, :originalCaregiverId, :status, :outcome,
			        :decidedBy, :assignedCaregiverId, :sequence)
			on duplicate key update
			    status = if(:sequence > sequence, :status, status),
			    outcome = if(:sequence > sequence, :outcome, outcome),
			    decided_by = if(:sequence > sequence, :decidedBy, decided_by),
			    assigned_caregiver_id = if(:sequence > sequence, :assignedCaregiverId, assigned_caregiver_id),
			    sequence = greatest(sequence, :sequence)
			""";

	private final JdbcClient jdbc;

	JdbcKeptFacts(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void keep(VisitScheduled event) {
		visitState(event.visitId(), event.elderId(), event.scheduledStart(), event.version(), "SCHEDULED",
				event.caregiverId(), null, null);
		jdbc.sql(VISIT_SCHEDULE)
				.param("visitId", event.visitId())
				.param("scheduledEnd", at(event.scheduledEnd()))
				.param("serviceType", event.serviceType())
				.param("carePlanId", event.carePlanId())
				.param("carePlanNodeId", event.carePlanNodeId())
				.update();
	}

	@Override
	public void keep(VisitCaregiverChanged event) {
		visitState(event.visitId(), event.elderId(), event.scheduledStart(), event.version(), "SCHEDULED",
				event.caregiverId(), null, null);
	}

	@Override
	public void keep(VisitCancelled event) {
		visitState(event.visitId(), event.elderId(), event.scheduledStart(), event.version(), "CANCELLED",
				event.caregiverId(), null, null);
	}

	@Override
	public void keep(VisitCheckedIn event) {
		visitState(event.visitId(), event.elderId(), event.scheduledStart(), event.version(), "IN_PROGRESS",
				event.caregiverId(), new Times(event.checkedInAt()), null);
	}

	@Override
	public void keep(VisitCompleted event) {
		visitState(event.visitId(), event.elderId(), event.scheduledStart(), event.version(), "COMPLETED",
				event.caregiverId(), new Times(event.checkedInAt()), new Times(event.checkedOutAt()));
	}

	@Override
	public void keep(VisitExceptionRaised event) {
		visitState(event.visitId(), event.elderId(), event.scheduledStart(), event.version(), "EXCEPTION",
				event.caregiverId(), null, null);
	}

	@Override
	public void keep(VisitTaskRecorded event, long sequence) {
		jdbc.sql(TASK)
				.param("taskId", event.taskId())
				.param("visitId", event.visitId())
				.param("elderId", event.elderId())
				.param("visitScheduledStart", at(event.visitScheduledStart()))
				.param("name", event.name())
				.param("status", event.status())
				.param("caregiverNote", event.caregiverNote())
				.param("recordedAt", at(event.recordedAt()))
				.param("sequence", sequence)
				.update();
	}

	@Override
	public void keep(VitalsRecorded event) {
		for (VitalsRecorded.Reading reading : event.readings()) {
			jdbc.sql(READING)
					.param("healthRecordId", event.healthRecordId())
					.param("visitId", event.visitId())
					.param("elderId", event.elderId())
					.param("visitScheduledStart", at(event.visitScheduledStart()))
					.param("metric", reading.metric())
					.param("value", new BigDecimal(reading.value()))
					.param("unit", reading.unit())
					.param("outOfRange", reading.outOfRange())
					.param("recordedAt", at(event.recordedAt()))
					.update();
		}
	}

	@Override
	public void keep(ElderConfirmationSubmitted event) {
		jdbc.sql(CONFIRMATION)
				.param("visitId", event.visitId())
				.param("elderId", event.elderId())
				.param("visitScheduledStart", at(event.visitScheduledStart()))
				.param("status", event.status())
				.param("rating", event.rating())
				.param("comment", event.comment())
				.param("confirmedAt", at(event.confirmedAt()))
				.update();
	}

	@Override
	public void keep(IncidentRaised event) {
		jdbc.sql(INCIDENT_RAISED)
				.param("incidentId", event.incidentId())
				.param("elderId", event.elderId())
				.param("visitId", event.visitId())
				.param("category", event.category())
				.param("description", event.description())
				.param("reportedAt", at(event.reportedAt()))
				.param("severity", event.severity())
				.param("status", event.status())
				.update();
	}

	@Override
	public void keep(IncidentUpdated event) {
		jdbc.sql(INCIDENT_LOG)
				.param("logId", event.logId())
				.param("incidentId", event.incidentId())
				.param("actor", event.actor())
				.param("action", event.action())
				.param("detail", event.detail())
				.param("occurredAt", at(event.occurredAt()))
				.update();
		jdbc.sql(INCIDENT_STATE)
				.param("incidentId", event.incidentId())
				.param("elderId", event.elderId())
				.param("severity", event.severity())
				.param("status", event.status())
				.param("resolvedAt", at(event.resolvedAt()))
				.param("logId", event.logId())
				.update();
	}

	@Override
	public void keep(SpotCheckUpdated event, long sequence) {
		jdbc.sql(SPOT_CHECK)
				.param("spotCheckId", event.spotCheckId())
				.param("elderId", event.elderId())
				.param("visitId", event.visitId())
				.param("caregiverId", event.caregiverId())
				.param("proposedTime", at(event.proposedTime()))
				.param("approvalStatus", event.approvalStatus())
				.param("result", event.result())
				.param("outcome", event.outcome())
				.param("finding", event.finding())
				.param("caregiverResponse", event.caregiverResponse())
				.param("checkedAt", at(event.checkedAt()))
				.param("sequence", sequence)
				.update();
	}

	@Override
	public void keep(RosterChangeUpdated event, long sequence) {
		jdbc.sql(ROSTER_CHANGE)
				.param("rosterChangeId", event.rosterChangeId())
				.param("visitId", event.visitId())
				.param("elderId", event.elderId())
				.param("visitStart", at(event.visitStart()))
				.param("originalCaregiverId", event.originalCaregiverId())
				.param("status", event.status())
				.param("outcome", event.outcome())
				.param("decidedBy", event.decidedBy())
				.param("assignedCaregiverId", event.assignedCaregiverId())
				.param("sequence", sequence)
				.update();
	}

	/** The check-in or check-out time an event sets, which may be empty; null when the event leaves it as it is. */
	private record Times(OffsetDateTime value) {
	}

	private void visitState(Long visitId, Long elderId, OffsetDateTime scheduledStart, Integer version, String status,
			Long caregiverId, Times checkedIn, Times checkedOut) {
		jdbc.sql(VISIT_STATE)
				.param("visitId", visitId)
				.param("elderId", elderId)
				.param("scheduledStart", at(scheduledStart))
				.param("status", status)
				.param("caregiverId", caregiverId)
				.param("checkedInAt", checkedIn == null ? null : at(checkedIn.value()))
				.param("checkedOutAt", checkedOut == null ? null : at(checkedOut.value()))
				.param("setsCheckIn", checkedIn != null)
				.param("setsCheckOut", checkedOut != null)
				.param("version", version)
				.update();
	}

	/** A business time on Singapore's clock, as the driver should be handed it (see the class comment). */
	static Timestamp at(OffsetDateTime time) {
		return time == null ? null : Timestamp.valueOf(SingaporeTime.local(time));
	}

}
