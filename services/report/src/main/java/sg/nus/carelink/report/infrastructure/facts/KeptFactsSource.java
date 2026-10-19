package sg.nus.carelink.report.infrastructure.facts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreNotFound;
import sg.nus.carelink.report.domain.model.ConfirmationFact;
import sg.nus.carelink.report.domain.model.ElderProfile;
import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.ObservationFact;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.domain.model.ReviewFact;
import sg.nus.carelink.report.domain.model.RosterChangeFact;
import sg.nus.carelink.report.domain.model.SpotCheckFact;
import sg.nus.carelink.report.domain.model.ValueAddedFact;
import sg.nus.carelink.report.domain.model.VisitFact;
import sg.nus.carelink.report.domain.model.VitalFact;
import sg.nus.carelink.report.domain.repository.ReportFactsSource;

/**
 * Reads a period's facts from report's own tables: the visits, readings, notes, answers, incidents,
 * spot checks and roster changes kept from visit's and core's events ({@link JdbcKeptFacts}), with
 * the family's reviews and the value-added requests report owns. Who the elder is, their plan and
 * primary caregiver, and the caregivers' names are asked of core when a report is made
 * (docs/platform/event-catalogue.md, section 4); nothing here reads another service's table.
 *
 * <p>Deliberately thin. Which visits count as not closed, how readings become ranges and what
 * an incident's conclusion was are all decided in the domain on the rows this returns, where
 * a unit test can reach them; what is left here is SQL and column mapping, which only a real
 * database can check ({@code ReportFlowIT}).
 *
 * <p><strong>Time goes in and out as {@link Timestamp}, not {@link LocalDateTime}.</strong>
 * The application's JVM runs in UTC while the JDBC URL declares the server to be in
 * Asia/Singapore, so the driver shifts every value it treats as an instant. A {@code Timestamp}
 * is sent as a TIMESTAMP and converted into the connection's zone: a visit at 09:00 is stored as
 * 17:00, and reads back as 09:00 because {@code getTimestamp} converts back. A
 * {@code LocalDateTime} handed to {@code JdbcClient} takes a different path - {@code setObject}
 * maps it to DATETIME and the driver sends it as it is. A boundary of Monday 00:00 bound that way
 * would be compared with stored values eight hours ahead of it, and Sunday evening's visits would
 * land in the next week. So the facts are written as {@code Timestamp} ({@link JdbcKeptFacts}),
 * the copy made at the first deployment kept the values the services had written the same way,
 * the boundaries are bound as {@code Timestamp.valueOf(...)} and the columns read with
 * {@code getTimestamp}: both sides of every comparison travel the same path.
 */
@Component
class KeptFactsSource implements ReportFactsSource {

	private static final String ELDERS_WITH_VISITS = """
			select distinct v.elder_id
			from report_visit v
			where v.scheduled_start >= :from and v.scheduled_start < :to
			order by v.elder_id
			""";

	private static final String VISITS = """
			select v.visit_id as id, v.caregiver_id, v.service_type, v.scheduled_start, v.status,
			       timestampdiff(minute, v.scheduled_start, v.scheduled_end) as planned_minutes,
			       timestampdiff(minute, v.checked_in_at, v.checked_out_at) as worked_minutes,
			       coalesce(ev.evidence_count,v.evidence_count) as evidence_count,
			       coalesce(ev.verified_evidence_count,v.verified_evidence_count) as verified_evidence_count,
			       exists (select 1 from report_roster_change rc
			                where rc.visit_id = v.visit_id and rc.outcome = 'SKIPPED'
			                  and rc.decided_by = 'FAMILY') as skipped_by_family
			from report_visit v left join report_visit_evidence ev on ev.visit_id=v.visit_id
			where v.elder_id = :elderId and v.scheduled_start >= :from and v.scheduled_start < :to
			order by v.scheduled_start, v.visit_id
			""";

	private static final String VITALS = """
			select s.visit_id, s.metric, s.value, s.unit, s.out_of_range, s.recorded_at
			from report_vital_sign s
			where s.elder_id = :elderId and s.visit_scheduled_start >= :from and s.visit_scheduled_start < :to
			order by s.recorded_at, s.id
			""";

	/** The caregivers' notes on the period's visits: what UC-CG05's write-off calls the observation summary. */
	private static final String OBSERVATIONS = """
			select t.visit_id, t.name, t.caregiver_note
			from report_visit_task t
			where t.elder_id = :elderId and t.visit_scheduled_start >= :from and t.visit_scheduled_start < :to
			  and t.caregiver_note is not null and trim(t.caregiver_note) <> ''
			order by t.visit_scheduled_start, t.task_id
			""";

	/** An incident is in no week until IncidentRaised has said when it was reported. */
	private static final String INCIDENTS = """
			select i.incident_id as id, i.category, i.severity, i.status, i.description, i.reported_at, i.resolved_at
			from report_incident i
			where i.elder_id = :elderId and i.reported_at >= :from and i.reported_at < :to
			order by i.reported_at, i.incident_id
			""";

	private static final String TIMELINE = """
			select l.actor, l.action, l.detail, l.occurred_at
			from report_incident_log l
			where l.incident_id = :incidentId
			order by l.occurred_at, l.log_id
			""";

	/** The elder's own answers about the period's visits (UC-EL01). */
	private static final String CONFIRMATIONS = """
			select c.visit_id, c.confirmation_status, c.rating, c.comment, c.confirmed_at
			from report_elder_confirmation c
			where c.elder_id = :elderId and c.visit_scheduled_start >= :from and c.visit_scheduled_start < :to
			order by c.visit_scheduled_start, c.visit_id
			""";

	/** The family's periodic reviews whose dates overlap the period (UC-FM09). DATE columns, compared as dates. */
	private static final String REVIEWS = """
			select r.caregiver_id, r.period_start, r.period_end, r.overall_rating, r.punctuality_score,
			       r.care_quality_score, r.feedback_notes, r.renewal_decision
			from caregiver_review r
			where r.elder_id = :elderId and r.period_start <= :lastDay and r.period_end >= :firstDay
			order by r.period_start, r.id
			""";

	/** Spot checks proposed for a moment in the period, whatever became of them (UC-MG08). */
	private static final String SPOT_CHECKS = """
			select s.spot_check_id as id, s.caregiver_id, s.proposed_time, s.approval_status, s.result, s.outcome,
			       s.finding, s.caregiver_response, s.checked_at
			from report_spot_check s
			where s.elder_id = :elderId and s.proposed_time >= :from and s.proposed_time < :to
			order by s.proposed_time, s.spot_check_id
			""";

	/** Visits of the period an absence left without their caregiver, and how each was settled (UC-MG04). */
	private static final String ROSTER_CHANGES = """
			select rc.visit_id, rc.visit_start, rc.original_caregiver_id, rc.status, rc.outcome, rc.decided_by,
			       rc.assigned_caregiver_id
			from report_roster_change rc
			where rc.elder_id = :elderId and rc.visit_start >= :from and rc.visit_start < :to
			order by rc.visit_start, rc.roster_change_id
			""";

	/** Value-added services asked for a moment in the period (UC-EL02, UC-FM08). */
	private static final String VALUE_ADDED = """
			select r.id, s.name as service, r.requested_schedule, r.status, r.visit_id
			from value_added_service_request r
			join value_added_service s on s.id = r.value_added_service_id
			where r.elder_id = :elderId and r.requested_schedule >= :from and r.requested_schedule < :to
			order by r.requested_schedule, r.id
			""";

	private final JdbcClient jdbc;
	private final CoreApi core;

	KeptFactsSource(JdbcClient jdbc, CoreApi core) {
		this.jdbc = jdbc;
		this.core = core;
	}

	@Override
	public Optional<ReportFacts> gather(Long elderId, ReportPeriod period) {
		Optional<ElderProfile> elder = profile(elderId);
		if (elder.isEmpty()) {
			return Optional.empty();
		}

		Timestamp from = boundary(period.startsAt());
		Timestamp to = boundary(period.endsBefore());
		CaregiverNames names = new CaregiverNames(core);

		List<VisitFact> visits = inPeriod(VISITS, elderId, from, to).query((rs, rowNum) -> visit(rs, names)).list();
		List<VitalFact> vitals = inPeriod(VITALS, elderId, from, to).query((rs, rowNum) -> vital(rs)).list();
		List<ObservationFact> observations =
				inPeriod(OBSERVATIONS, elderId, from, to).query((rs, rowNum) -> observation(rs)).list();
		List<IncidentFact> incidents = inPeriod(INCIDENTS, elderId, from, to)
				.query((rs, rowNum) -> incident(rs))
				.list()
				.stream()
				.map(this::withTimeline)
				.toList();

		ReportFacts.Quality quality = new ReportFacts.Quality(
				inPeriod(CONFIRMATIONS, elderId, from, to).query((rs, rowNum) -> confirmation(rs)).list(),
				jdbc.sql(REVIEWS).param("elderId", elderId)
						.param("firstDay", period.start()).param("lastDay", period.end())
						.query((rs, rowNum) -> review(rs, names)).list(),
				inPeriod(SPOT_CHECKS, elderId, from, to).query((rs, rowNum) -> spotCheck(rs, names)).list());
		ReportFacts.Changes changes = new ReportFacts.Changes(
				inPeriod(ROSTER_CHANGES, elderId, from, to).query((rs, rowNum) -> rosterChange(rs, names)).list(),
				inPeriod(VALUE_ADDED, elderId, from, to).query((rs, rowNum) -> valueAdded(rs)).list());

		return Optional.of(new ReportFacts(elderId, period, visits, vitals, observations, incidents, elder.get(),
				quality, changes));
	}

	@Override
	public List<Long> eldersWithVisitsIn(ReportPeriod period) {
		return jdbc.sql(ELDERS_WITH_VISITS)
				.param("from", boundary(period.startsAt()))
				.param("to", boundary(period.endsBefore()))
				.query(Long.class)
				.list();
	}

	/** Who the elder is, from core; empty when core has no such elder. */
	private Optional<ElderProfile> profile(Long elderId) {
		try {
			return Optional.of(profile(core.elderReportProfile(elderId)));
		}
		catch (CoreNotFound unknown) {
			return Optional.empty();
		}
	}

	private JdbcClient.StatementSpec inPeriod(String sql, Long elderId, Timestamp from, Timestamp to) {
		return jdbc.sql(sql).param("elderId", elderId).param("from", from).param("to", to);
	}

	/** An incident's timeline is read per incident: a week has a handful at most, and the SQL stays plain. */
	private IncidentFact withTimeline(IncidentFact incident) {
		List<IncidentFact.Step> timeline = jdbc.sql(TIMELINE)
				.param("incidentId", incident.id())
				.query((rs, rowNum) -> step(rs))
				.list();
		return new IncidentFact(
				incident.id(), incident.category(), incident.severity(), incident.status(), incident.description(),
				incident.reportedAt(), incident.resolvedAt(), timeline);
	}

	/**
	 * Caregivers' names, asked of core once per caregiver while one report's facts are gathered. A
	 * caregiver core no longer has is shown without a name, as the rows without one were.
	 */
	static final class CaregiverNames {

		private final CoreApi core;
		private final Map<Long, Optional<String>> asked = new HashMap<>();

		CaregiverNames(CoreApi core) {
			this.core = core;
		}

		String of(Long caregiverId) {
			if (caregiverId == null) {
				return null;
			}
			return asked.computeIfAbsent(caregiverId,
					id -> core.findCaregiverPublicProfile(id).map(CoreApi.CaregiverPublicProfile::fullName))
					.orElse(null);
		}

	}

	// ----------------------------------------------------------------------- rows ---

	/** A period boundary in the form the facts were written in. See the class comment. */
	static Timestamp boundary(LocalDateTime moment) {
		return Timestamp.valueOf(moment);
	}

	/** A DATETIME column read back the way it was written. See the class comment. */
	static LocalDateTime moment(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toLocalDateTime();
	}

	/** A nullable id column, as null rather than the 0 {@code getLong} reports for it. */
	static Long id(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	/** A nullable whole-number column, as null rather than 0. */
	static Integer number(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	static VisitFact visit(ResultSet rs, CaregiverNames names) throws SQLException {
		Long caregiverId = id(rs, "caregiver_id");
		return new VisitFact(
				rs.getLong("id"),
				caregiverId,
				names.of(caregiverId),
				rs.getString("service_type"),
				moment(rs, "scheduled_start"),
				VisitFact.Status.valueOf(rs.getString("status")),
				rs.getInt("evidence_count"),
				rs.getInt("verified_evidence_count"),
				rs.getBoolean("skipped_by_family"),
				number(rs, "planned_minutes"),
				number(rs, "worked_minutes"));
	}

	static ElderProfile profile(CoreApi.ElderReportProfile elder) {
		return new ElderProfile(
				elder.fullName(),
				elder.gender(),
				elder.dateOfBirth(),
				elder.mobilityLevel(),
				elder.livesAlone(),
				elder.medicalNotes(),
				elder.primaryCaregiverId(),
				elder.primaryCaregiverName(),
				elder.planVersion(),
				elder.planWeeklyHours());
	}

	static ConfirmationFact confirmation(ResultSet rs) throws SQLException {
		return new ConfirmationFact(
				rs.getLong("visit_id"),
				"DISPUTED".equals(rs.getString("confirmation_status")),
				number(rs, "rating"),
				rs.getString("comment"),
				moment(rs, "confirmed_at"));
	}

	static ReviewFact review(ResultSet rs, CaregiverNames names) throws SQLException {
		long caregiverId = rs.getLong("caregiver_id");
		return new ReviewFact(
				caregiverId,
				names.of(caregiverId),
				rs.getObject("period_start", LocalDate.class),
				rs.getObject("period_end", LocalDate.class),
				rs.getInt("overall_rating"),
				number(rs, "punctuality_score"),
				number(rs, "care_quality_score"),
				rs.getString("feedback_notes"),
				rs.getString("renewal_decision"));
	}

	static SpotCheckFact spotCheck(ResultSet rs, CaregiverNames names) throws SQLException {
		Long caregiverId = id(rs, "caregiver_id");
		return new SpotCheckFact(
				rs.getLong("id"),
				caregiverId,
				names.of(caregiverId),
				moment(rs, "proposed_time"),
				rs.getString("approval_status"),
				rs.getString("result"),
				rs.getString("outcome"),
				rs.getString("finding"),
				rs.getString("caregiver_response"),
				moment(rs, "checked_at"));
	}

	static RosterChangeFact rosterChange(ResultSet rs, CaregiverNames names) throws SQLException {
		Long original = id(rs, "original_caregiver_id");
		Long assigned = id(rs, "assigned_caregiver_id");
		return new RosterChangeFact(
				rs.getLong("visit_id"),
				moment(rs, "visit_start"),
				original,
				names.of(original),
				rs.getString("status"),
				rs.getString("outcome"),
				rs.getString("decided_by"),
				assigned,
				names.of(assigned));
	}

	static ValueAddedFact valueAdded(ResultSet rs) throws SQLException {
		return new ValueAddedFact(
				rs.getLong("id"),
				rs.getString("service"),
				moment(rs, "requested_schedule"),
				rs.getString("status"),
				id(rs, "visit_id"));
	}

	static VitalFact vital(ResultSet rs) throws SQLException {
		return new VitalFact(
				rs.getLong("visit_id"),
				rs.getString("metric"),
				rs.getBigDecimal("value"),
				rs.getString("unit"),
				rs.getBoolean("out_of_range"),
				moment(rs, "recorded_at"));
	}

	static ObservationFact observation(ResultSet rs) throws SQLException {
		return new ObservationFact(rs.getLong("visit_id"), rs.getString("name"), rs.getString("caregiver_note").strip());
	}

	static IncidentFact incident(ResultSet rs) throws SQLException {
		return new IncidentFact(
				rs.getLong("id"),
				rs.getString("category"),
				rs.getString("severity"),
				rs.getString("status"),
				rs.getString("description"),
				moment(rs, "reported_at"),
				moment(rs, "resolved_at"),
				List.of());
	}

	static IncidentFact.Step step(ResultSet rs) throws SQLException {
		return new IncidentFact.Step(
				rs.getString("actor"),
				rs.getString("action"),
				rs.getString("detail"),
				moment(rs, "occurred_at"));
	}
}
