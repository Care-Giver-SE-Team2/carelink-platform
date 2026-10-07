package sg.nus.carelink.report.infrastructure.facts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.ObservationFact;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.domain.model.VisitFact;
import sg.nus.carelink.report.domain.model.VitalFact;
import sg.nus.carelink.report.domain.repository.ReportFactsSource;

/**
 * Reads a period's visits, readings, notes and incidents straight from their tables.
 *
 * <p><strong>Why plain queries rather than calls into the visit and incident modules.</strong>
 * The report module may not depend on either (the slice rule in {@code LayerDependencyTest}),
 * and neither offers "everything for one elder between two moments". Reading is not owning:
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
			       (select count(*) from visit_evidence e where e.visit_id = v.id) as evidence_count,
			       (select count(*) from visit_evidence e
			         where e.visit_id = v.id and e.verification_status = 'VERIFIED') as verified_evidence_count
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

		return Optional.of(new ReportFacts(elderId, period, visits, vitals, observations, incidents));
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

	static VisitFact visit(ResultSet rs) throws SQLException {
		long caregiverId = rs.getLong("caregiver_id");
		boolean unassigned = rs.wasNull();
		return new VisitFact(
				rs.getLong("id"),
				unassigned ? null : caregiverId,
				rs.getString("caregiver_name"),
				rs.getString("service_type"),
				moment(rs, "scheduled_start"),
				VisitFact.Status.valueOf(rs.getString("status")),
				rs.getInt("evidence_count"),
				rs.getInt("verified_evidence_count"));
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
