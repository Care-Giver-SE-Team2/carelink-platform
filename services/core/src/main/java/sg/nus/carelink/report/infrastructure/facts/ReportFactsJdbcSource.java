package sg.nus.carelink.report.infrastructure.facts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

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
 * Reads a period's visits, readings, notes and incidents straight from their tables, with the
 * elder's profile and plan, the elder's answers, the family's reviews, the spot checks, the
 * roster changes and the value-added requests that belong to the same period.
 *
 * <p><strong>Why plain queries rather than calls into the other modules.</strong>
 * The report module may not depend on them (the slice rule in {@code LayerDependencyTest}),
 * and none offers "everything for one elder between two moments". Reading is not owning:
 * these statements take an id and two moments, write nothing, and return the report's own
 * records, never another module's classes - the same arrangement as the incident module's
 * {@code NotificationTableAlert}.
 *
 * <p>Deliberately thin. Which visits count as not closed, how readings become ranges and what
 * an incident's conclusion was are all decided in the domain on the rows this returns, where
 * a unit test can reach them; what is left here is SQL and column mapping, which only a real
 * database can check ({@code ReportFlowIT}).
 *
 * <p><strong>Time goes in and out as {@link Timestamp}, not {@link LocalDateTime}.</strong>
 * The application's JVM runs in UTC while the JDBC URL declares the server to be in
 * Asia/Singapore, so the driver shifts every value it treats as an instant. Hibernate writes
 * a {@code LocalDateTime} as a {@code Timestamp}, which Connector/J (9.7) sends as a
 * TIMESTAMP and converts into the connection's zone: a visit at 09:00 is stored as 17:00, and
 * reads back as 09:00 because {@code getTimestamp} converts back. A {@code LocalDateTime}
 * handed to {@code JdbcClient} takes a different path - {@code setObject} maps it to DATETIME
 * and the driver sends it as it is. A boundary of Monday 00:00 bound that way would be
 * compared with stored values eight hours ahead of it, and Sunday evening's visits would land
 * in the next week. So the boundaries are bound as {@code Timestamp.valueOf(...)} and the
 * columns read with {@code getTimestamp}: both sides of every comparison travel the path the
 * rows were written by.
 */
@Component
class ReportFactsJdbcSource implements ReportFactsSource {

	private static final String ELDER_EXISTS = """
			select count(*) from elder where id = :elderId
			""";

	private static final String ELDERS_WITH_VISITS = """
			select distinct v.elder_id
			from visit v
			where v.scheduled_start >= :from and v.scheduled_start < :to
			order by v.elder_id
			""";

	private static final String VISITS = """
			select v.id, v.caregiver_id, c.full_name as caregiver_name, v.service_type,
			       v.scheduled_start, v.status,
			       timestampdiff(minute, v.scheduled_start, v.scheduled_end) as planned_minutes,
			       timestampdiff(minute, v.checked_in_at, v.checked_out_at) as worked_minutes,
			       (select count(*) from visit_evidence e where e.visit_id = v.id) as evidence_count,
			       (select count(*) from visit_evidence e
			         where e.visit_id = v.id and e.verification_status = 'VERIFIED') as verified_evidence_count,
			       exists (select 1 from roster_change rc
			                where rc.visit_id = v.id and rc.outcome = 'SKIPPED'
			                  and rc.decided_by = 'FAMILY') as skipped_by_family
			from visit v
			left join caregiver c on c.id = v.caregiver_id
			where v.elder_id = :elderId and v.scheduled_start >= :from and v.scheduled_start < :to
			order by v.scheduled_start, v.id
			""";

	private static final String VITALS = """
			select s.visit_id, s.metric, s.value, s.unit, s.out_of_range, s.recorded_at
			from vital_sign s
			join visit v on v.id = s.visit_id
			where v.elder_id = :elderId and v.scheduled_start >= :from and v.scheduled_start < :to
			order by s.recorded_at, s.id
			""";

	/** The caregivers' notes on the period's visits: what UC-CG05's write-off calls the observation summary. */
	private static final String OBSERVATIONS = """
			select t.visit_id, t.name, t.caregiver_note
			from visit_task t
			join visit v on v.id = t.visit_id
			where v.elder_id = :elderId and v.scheduled_start >= :from and v.scheduled_start < :to
			  and t.caregiver_note is not null and trim(t.caregiver_note) <> ''
			order by v.scheduled_start, t.id
			""";

	private static final String INCIDENTS = """
			select i.id, i.category, i.severity, i.status, i.description, i.reported_at, i.resolved_at
			from incident i
			where i.elder_id = :elderId and i.reported_at >= :from and i.reported_at < :to
			order by i.reported_at, i.id
			""";

	private static final String TIMELINE = """
			select l.actor, l.action, l.detail, l.occurred_at
			from incident_log l
			where l.incident_id = :incidentId
			order by l.occurred_at, l.id
			""";

	/**
	 * Who the elder is, the primary caregiver, and the latest published plan. The plan is the
	 * one in force when the report is generated; a plan stopped since has none.
	 */
	private static final String ELDER_PROFILE = """
			select e.full_name, e.gender, e.date_of_birth, e.mobility_level, e.lives_alone, e.medical_notes,
			       pc.caregiver_id as primary_caregiver_id, c.full_name as primary_caregiver_name,
			       p.version as plan_version, p.total_hours as plan_hours
			from elder e
			left join elder_primary_caregiver pc on pc.elder_id = e.id
			left join caregiver c on c.id = pc.caregiver_id
			left join care_plan p on p.id = (
			    select cp.id from care_plan cp
			    where cp.elder_id = e.id and cp.status = 'PUBLISHED'
			    order by cp.version desc, cp.id desc
			    limit 1)
			where e.id = :elderId
			""";

	/** The elder's own answers about the period's visits (UC-EL01). */
	private static final String CONFIRMATIONS = """
			select c.visit_id, c.confirmation_status, c.rating, c.comment, c.confirmed_at
			from elder_confirmation c
			join visit v on v.id = c.visit_id
			where v.elder_id = :elderId and v.scheduled_start >= :from and v.scheduled_start < :to
			order by v.scheduled_start, c.id
			""";

	/** The family's periodic reviews whose dates overlap the period (UC-FM09). DATE columns, compared as dates. */
	private static final String REVIEWS = """
			select r.caregiver_id, c.full_name as caregiver_name, r.period_start, r.period_end,
			       r.overall_rating, r.punctuality_score, r.care_quality_score, r.feedback_notes, r.renewal_decision
			from caregiver_review r
			left join caregiver c on c.id = r.caregiver_id
			where r.elder_id = :elderId and r.period_start <= :lastDay and r.period_end >= :firstDay
			order by r.period_start, r.id
			""";

	/** Spot checks proposed for a moment in the period, whatever became of them (UC-MG08). */
	private static final String SPOT_CHECKS = """
			select s.id, s.caregiver_id, c.full_name as caregiver_name, s.proposed_time, s.approval_status,
			       s.result, s.outcome, s.finding, s.caregiver_response, s.checked_at
			from spot_check s
			left join caregiver c on c.id = s.caregiver_id
			where s.elder_id = :elderId and s.proposed_time >= :from and s.proposed_time < :to
			order by s.proposed_time, s.id
			""";

	/** Visits of the period an absence left without their caregiver, and how each was settled (UC-MG04). */
	private static final String ROSTER_CHANGES = """
			select rc.visit_id, rc.visit_start, rc.original_caregiver_id, oc.full_name as original_caregiver_name,
			       rc.status, rc.outcome, rc.decided_by, rc.assigned_caregiver_id, ac.full_name as assigned_caregiver_name
			from roster_change rc
			left join caregiver oc on oc.id = rc.original_caregiver_id
			left join caregiver ac on ac.id = rc.assigned_caregiver_id
			where rc.elder_id = :elderId and rc.visit_start >= :from and rc.visit_start < :to
			order by rc.visit_start, rc.id
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

	ReportFactsJdbcSource(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<ReportFacts> gather(Long elderId, ReportPeriod period) {
		Long elders = jdbc.sql(ELDER_EXISTS).param("elderId", elderId).query(Long.class).single();
		if (elders == null || elders == 0) {
			return Optional.empty();
		}

		Timestamp from = boundary(period.startsAt());
		Timestamp to = boundary(period.endsBefore());

		List<VisitFact> visits = inPeriod(VISITS, elderId, from, to).query((rs, rowNum) -> visit(rs)).list();
		List<VitalFact> vitals = inPeriod(VITALS, elderId, from, to).query((rs, rowNum) -> vital(rs)).list();
		List<ObservationFact> observations =
				inPeriod(OBSERVATIONS, elderId, from, to).query((rs, rowNum) -> observation(rs)).list();
		List<IncidentFact> incidents = inPeriod(INCIDENTS, elderId, from, to)
				.query((rs, rowNum) -> incident(rs))
				.list()
				.stream()
				.map(this::withTimeline)
				.toList();

		ElderProfile elder = jdbc.sql(ELDER_PROFILE).param("elderId", elderId)
				.query((rs, rowNum) -> profile(rs)).optional().orElse(ElderProfile.UNKNOWN);
		ReportFacts.Quality quality = new ReportFacts.Quality(
				inPeriod(CONFIRMATIONS, elderId, from, to).query((rs, rowNum) -> confirmation(rs)).list(),
				jdbc.sql(REVIEWS).param("elderId", elderId)
						.param("firstDay", period.start()).param("lastDay", period.end())
						.query((rs, rowNum) -> review(rs)).list(),
				inPeriod(SPOT_CHECKS, elderId, from, to).query((rs, rowNum) -> spotCheck(rs)).list());
		ReportFacts.Changes changes = new ReportFacts.Changes(
				inPeriod(ROSTER_CHANGES, elderId, from, to).query((rs, rowNum) -> rosterChange(rs)).list(),
				inPeriod(VALUE_ADDED, elderId, from, to).query((rs, rowNum) -> valueAdded(rs)).list());

		return Optional.of(new ReportFacts(elderId, period, visits, vitals, observations, incidents, elder, quality, changes));
	}

	@Override
	public List<Long> eldersWithVisitsIn(ReportPeriod period) {
		return jdbc.sql(ELDERS_WITH_VISITS)
				.param("from", boundary(period.startsAt()))
				.param("to", boundary(period.endsBefore()))
				.query(Long.class)
				.list();
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

	// ----------------------------------------------------------------------- rows ---

	/** A period boundary in the form Hibernate writes the columns it is compared with. See the class comment. */
	static Timestamp boundary(LocalDateTime moment) {
		return Timestamp.valueOf(moment);
	}

	/** A DATETIME column read back the way Hibernate reads it. See the class comment. */
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

	static VisitFact visit(ResultSet rs) throws SQLException {
		return new VisitFact(
				rs.getLong("id"),
				id(rs, "caregiver_id"),
				rs.getString("caregiver_name"),
				rs.getString("service_type"),
				moment(rs, "scheduled_start"),
				VisitFact.Status.valueOf(rs.getString("status")),
				rs.getInt("evidence_count"),
				rs.getInt("verified_evidence_count"),
				rs.getBoolean("skipped_by_family"),
				number(rs, "planned_minutes"),
				number(rs, "worked_minutes"));
	}

	/** DATE columns are read as dates: they carry no time of day, so nothing shifts them. */
	static ElderProfile profile(ResultSet rs) throws SQLException {
		boolean livesAlone = rs.getBoolean("lives_alone");
		Boolean known = rs.wasNull() ? null : livesAlone;
		return new ElderProfile(
				rs.getString("full_name"),
				rs.getString("gender"),
				rs.getObject("date_of_birth", LocalDate.class),
				rs.getString("mobility_level"),
				known,
				rs.getString("medical_notes"),
				id(rs, "primary_caregiver_id"),
				rs.getString("primary_caregiver_name"),
				number(rs, "plan_version"),
				rs.getBigDecimal("plan_hours"));
	}

	static ConfirmationFact confirmation(ResultSet rs) throws SQLException {
		return new ConfirmationFact(
				rs.getLong("visit_id"),
				"DISPUTED".equals(rs.getString("confirmation_status")),
				number(rs, "rating"),
				rs.getString("comment"),
				moment(rs, "confirmed_at"));
	}

	static ReviewFact review(ResultSet rs) throws SQLException {
		return new ReviewFact(
				rs.getLong("caregiver_id"),
				rs.getString("caregiver_name"),
				rs.getObject("period_start", LocalDate.class),
				rs.getObject("period_end", LocalDate.class),
				rs.getInt("overall_rating"),
				number(rs, "punctuality_score"),
				number(rs, "care_quality_score"),
				rs.getString("feedback_notes"),
				rs.getString("renewal_decision"));
	}

	static SpotCheckFact spotCheck(ResultSet rs) throws SQLException {
		return new SpotCheckFact(
				rs.getLong("id"),
				id(rs, "caregiver_id"),
				rs.getString("caregiver_name"),
				moment(rs, "proposed_time"),
				rs.getString("approval_status"),
				rs.getString("result"),
				rs.getString("outcome"),
				rs.getString("finding"),
				rs.getString("caregiver_response"),
				moment(rs, "checked_at"));
	}

	static RosterChangeFact rosterChange(ResultSet rs) throws SQLException {
		return new RosterChangeFact(
				rs.getLong("visit_id"),
				moment(rs, "visit_start"),
				rs.getLong("original_caregiver_id"),
				rs.getString("original_caregiver_name"),
				rs.getString("status"),
				rs.getString("outcome"),
				rs.getString("decided_by"),
				id(rs, "assigned_caregiver_id"),
				rs.getString("assigned_caregiver_name"));
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
