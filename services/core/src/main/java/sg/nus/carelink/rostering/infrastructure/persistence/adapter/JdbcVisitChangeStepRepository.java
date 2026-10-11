package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;
import sg.nus.carelink.rostering.domain.model.VisitChangeStep;
import sg.nus.carelink.rostering.domain.repository.VisitChangeStepRepository;

/**
 * {@link VisitChangeStepRepository} in {@code visit_change_step}, with plain statements: a step moves on
 * with one conditional update, which is what lets the request and the sweep race for it safely.
 */
@Repository
class JdbcVisitChangeStepRepository implements VisitChangeStepRepository {

	private static final String COLUMNS = """
			id, absence_id, roster_change_id, visit_id, elder_id, care_plan_id, service_type, visit_start, visit_end,
			original_caregiver_id, kind, to_caregiver_id, new_start, by_user_id, rostering_candidate_id, reason,
			rostering_run_id, decided_by, decided_by_user_id, caregiver_name, note, request_key, status, attempts,
			next_attempt_at, last_error, result_visit_id, incident_id, created_at, updated_at
			""";

	private static final String INSERT = """
			insert into visit_change_step (absence_id, roster_change_id, visit_id, elder_id, care_plan_id,
			    service_type, visit_start, visit_end, original_caregiver_id, kind, to_caregiver_id, new_start,
			    by_user_id, rostering_candidate_id, reason, rostering_run_id, decided_by, decided_by_user_id,
			    caregiver_name, note, request_key, status, attempts, next_attempt_at, last_error, result_visit_id,
			    incident_id, created_at, updated_at)
			values (:absenceId, :rosterChangeId, :visitId, :elderId, :carePlanId, :serviceType, :visitStart,
			    :visitEnd, :originalCaregiverId, :kind, :toCaregiverId, :newStart, :byUserId, :rosteringCandidateId,
			    :reason, :runId, :decidedBy, :decidedByUserId, :caregiverName, :note, :requestKey, :status,
			    :attempts, :nextTryAt, :lastError, :resultVisitId, :incidentId, :createdAt, :updatedAt)
			""";

	private static final String ADVANCE = """
			update visit_change_step
			set roster_change_id = :rosterChangeId, status = :status, attempts = :attempts,
			    next_attempt_at = :nextTryAt, last_error = :lastError, result_visit_id = :resultVisitId,
			    incident_id = :incidentId, updated_at = :updatedAt
			where id = :id and status = :fromStatus and attempts = :fromAttempts
			""";

	private final JdbcClient jdbc;

	JdbcVisitChangeStepRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public VisitChangeStep add(VisitChangeStep step) {
		VacatedSlot visit = step.visit();
		VisitChangeStep.Request request = step.request();
		VisitChangeStep.Decision decision = step.decision();
		KeyHolder id = new GeneratedKeyHolder();
		jdbc.sql(INSERT)
				.param("absenceId", step.absenceId())
				.param("rosterChangeId", step.rosterChangeId())
				.param("visitId", visit.visitId())
				.param("elderId", visit.elderId())
				.param("carePlanId", visit.carePlanId())
				.param("serviceType", visit.serviceType())
				.param("visitStart", time(visit.start()))
				.param("visitEnd", time(visit.end()))
				.param("originalCaregiverId", visit.vacatedByCaregiverId())
				.param("kind", request.kind().name())
				.param("toCaregiverId", request.toCaregiverId())
				.param("newStart", time(request.newStart()))
				.param("byUserId", request.byUserId())
				.param("rosteringCandidateId", request.rosteringCandidateId())
				.param("reason", request.reason())
				.param("runId", decision.runId())
				.param("decidedBy", decision.decidedBy() == null ? null : decision.decidedBy().name())
				.param("decidedByUserId", decision.decidedByUserId())
				.param("caregiverName", decision.caregiverName())
				.param("note", decision.note())
				.param("requestKey", step.requestKey())
				.param("status", step.status().name())
				.param("attempts", step.attempts())
				.param("nextTryAt", time(step.nextTryAt()))
				.param("lastError", step.lastError())
				.param("resultVisitId", step.resultVisitId())
				.param("incidentId", step.incidentId())
				.param("createdAt", time(step.createdAt()))
				.param("updatedAt", time(step.updatedAt()))
				.update(id, "id");
		Number added = id.getKeyAs(Number.class);
		if (added == null) {
			throw new IllegalStateException("MySQL gave the new visit change step no id");
		}
		return findById(added.longValue()).orElseThrow();
	}

	@Override
	public Optional<VisitChangeStep> findById(Long id) {
		return jdbc.sql("select " + COLUMNS + " from visit_change_step where id = :id")
				.param("id", id)
				.query(JdbcVisitChangeStepRepository::step)
				.optional();
	}

	@Override
	public boolean advance(VisitChangeStep from, VisitChangeStep to) {
		return jdbc.sql(ADVANCE)
				.param("rosterChangeId", to.rosterChangeId())
				.param("status", to.status().name())
				.param("attempts", to.attempts())
				.param("nextTryAt", time(to.nextTryAt()))
				.param("lastError", to.lastError())
				.param("resultVisitId", to.resultVisitId())
				.param("incidentId", to.incidentId())
				.param("updatedAt", time(to.updatedAt()))
				.param("id", from.id())
				.param("fromStatus", from.status().name())
				.param("fromAttempts", from.attempts())
				.update() == 1;
	}

	@Override
	public List<VisitChangeStep> findHoldingByAbsenceId(Long absenceId) {
		return jdbc.sql("select " + COLUMNS + " from visit_change_step where absence_id = :absenceId"
						+ " and status in ('PENDING', 'ABANDONED') order by id")
				.param("absenceId", absenceId)
				.query(JdbcVisitChangeStepRepository::step)
				.list();
	}

	@Override
	public List<VisitChangeStep> findDue(LocalDateTime now, int limit) {
		return jdbc.sql("select " + COLUMNS + " from visit_change_step where status = 'PENDING'"
						+ " and next_attempt_at <= :now order by next_attempt_at, id limit :limit")
				.param("now", time(now))
				.param("limit", limit)
				.query(JdbcVisitChangeStepRepository::step)
				.list();
	}

	@Override
	public List<VisitChangeStep> findGivenUpWithoutIncident(int limit) {
		return jdbc.sql("select " + COLUMNS + " from visit_change_step where status = 'ABANDONED'"
						+ " and incident_id is null order by id limit :limit")
				.param("limit", limit)
				.query(JdbcVisitChangeStepRepository::step)
				.list();
	}

	private static VisitChangeStep step(ResultSet row, int number) throws SQLException {
		VacatedSlot visit = new VacatedSlot(row.getLong("visit_id"), row.getLong("elder_id"),
				row.getObject("care_plan_id", Long.class), row.getString("service_type"), time(row, "visit_start"),
				time(row, "visit_end"), row.getObject("original_caregiver_id", Long.class));
		VisitChangeStep.Request request = new VisitChangeStep.Request(
				VisitChangeStep.Kind.valueOf(row.getString("kind")), row.getObject("to_caregiver_id", Long.class),
				time(row, "new_start"), row.getObject("by_user_id", Long.class),
				row.getObject("rostering_candidate_id", Long.class), row.getString("reason"));
		String decidedBy = row.getString("decided_by");
		VisitChangeStep.Decision decision = new VisitChangeStep.Decision(row.getObject("rostering_run_id", Long.class),
				decidedBy == null ? null : RosterChange.DecidedBy.valueOf(decidedBy),
				row.getObject("decided_by_user_id", Long.class), row.getString("caregiver_name"), row.getString("note"));
		return new VisitChangeStep(row.getLong("id"), row.getLong("absence_id"),
				row.getObject("roster_change_id", Long.class), visit, request, decision, row.getString("request_key"),
				VisitChangeStep.Status.valueOf(row.getString("status")), row.getInt("attempts"),
				time(row, "next_attempt_at"), row.getString("last_error"), row.getObject("result_visit_id", Long.class),
				row.getObject("incident_id", Long.class), time(row, "created_at"), time(row, "updated_at"));
	}

	private static Timestamp time(LocalDateTime value) {
		return value == null ? null : Timestamp.valueOf(value);
	}

	private static LocalDateTime time(ResultSet row, String column) throws SQLException {
		Timestamp value = row.getTimestamp(column);
		return value == null ? null : value.toLocalDateTime();
	}
}
