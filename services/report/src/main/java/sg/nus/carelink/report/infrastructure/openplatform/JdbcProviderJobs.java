package sg.nus.carelink.report.infrastructure.openplatform;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import sg.nus.carelink.report.domain.model.ProviderJob;
import sg.nus.carelink.report.domain.repository.ProviderJobs;

/**
 * {@link ProviderJobs} in {@code report_provider_job} and {@code report_provider_job_key} (migration V3).
 * Times are bound and read as {@link LocalDateTime}: Singapore wall-clock time, as the JPA entities
 * beside them write theirs.
 */
@Component
class JdbcProviderJobs implements ProviderJobs {

	private static final String SELECT = "select * from report_provider_job ";

	private final JdbcClient jdbc;

	JdbcProviderJobs(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public ProviderJob create(ProviderJob job) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
				insert into report_provider_job (provider_id, value_added_service_request_id, service_name, district,
				    area_name, elder_summary, languages, booked_by, scheduled_start, duration_minutes, status,
				    respond_by, dispatched_at, version)
				values (:providerId, :requestId, :serviceName, :district, :areaName, :elderSummary, :languages,
				    :bookedBy, :scheduledStart, :durationMinutes, :status, :respondBy, :dispatchedAt, :version)
				""")
				.param("providerId", job.providerId())
				.param("requestId", job.requestId())
				.param("serviceName", job.serviceName())
				.param("district", job.district())
				.param("areaName", job.areaName())
				.param("elderSummary", job.elderSummary())
				.param("languages", job.languages())
				.param("bookedBy", job.bookedBy())
				.param("scheduledStart", job.scheduledStart())
				.param("durationMinutes", job.durationMinutes())
				.param("status", job.status().name())
				.param("respondBy", job.respondBy())
				.param("dispatchedAt", job.dispatchedAt())
				.param("version", job.version())
				.update(keys, "id");
		return find(keys.getKey().longValue()).orElseThrow();
	}

	@Override
	public void update(ProviderJob job) {
		ProviderJob.Decline decline = job.decline();
		ProviderJob.Completion completion = job.completion();
		int updated = jdbc.sql("""
				update report_provider_job
				set status = :status, accepted_at = :acceptedAt, declined_at = :declinedAt,
				    decline_reason = :declineReason, decline_note = :declineNote, completed_at = :completedAt,
				    actual_start = :actualStart, actual_end = :actualEnd, completion_note = :completionNote,
				    cancelled_at = :cancelledAt, cancel_reason = :cancelReason, version = :version
				where id = :id and version = :version - 1
				""")
				.param("id", job.id())
				.param("status", job.status().name())
				.param("acceptedAt", job.acceptedAt())
				.param("declinedAt", decline == null ? null : decline.at())
				.param("declineReason", decline == null ? null : decline.reason().name())
				.param("declineNote", decline == null ? null : decline.note())
				.param("completedAt", completion == null ? null : completion.at())
				.param("actualStart", completion == null ? null : completion.actualStart())
				.param("actualEnd", completion == null ? null : completion.actualEnd())
				.param("completionNote", completion == null ? null : completion.note())
				.param("cancelledAt", job.cancelledAt())
				.param("cancelReason", job.cancelReason())
				.param("version", job.version())
				.update();
		if (updated != 1) {
			throw new org.springframework.dao.OptimisticLockingFailureException(
					"Provider job " + job.id() + " changed while it was updated");
		}
	}

	@Override
	public Optional<ProviderJob> find(Long jobId) {
		return jdbc.sql(SELECT + "where id = ?").param(jobId).query(this::row).optional();
	}

	@Override
	public Optional<ProviderJob> findForUpdate(Long jobId) {
		return jdbc.sql(SELECT + "where id = ? for update").param(jobId).query(this::row).optional();
	}

	@Override
	public Optional<ProviderJob> findByRequestId(Long requestId) {
		return jdbc.sql(SELECT + "where value_added_service_request_id = ?")
				.param(requestId)
				.query(this::row)
				.optional();
	}

	@Override
	public Optional<ProviderJob> findByRequestIdForUpdate(Long requestId) {
		return jdbc.sql(SELECT + "where value_added_service_request_id = ? for update")
				.param(requestId)
				.query(this::row)
				.optional();
	}

	@Override
	public List<ProviderJob> findByProvider(Long providerId, Set<ProviderJob.Status> statuses, int page, int size) {
		return jdbc.sql(SELECT + "where provider_id = :providerId" + statusFilter(statuses)
				+ " order by dispatched_at desc, id desc limit :size offset :offset")
				.param("providerId", providerId)
				.param("statuses", names(statuses))
				.param("size", size)
				.param("offset", (long) page * size)
				.query(this::row)
				.list();
	}

	@Override
	public long countByProvider(Long providerId, Set<ProviderJob.Status> statuses) {
		return jdbc.sql("select count(*) from report_provider_job where provider_id = :providerId"
				+ statusFilter(statuses))
				.param("providerId", providerId)
				.param("statuses", names(statuses))
				.query(Long.class)
				.single();
	}

	@Override
	public Map<ProviderJob.Status, Long> countsByStatus(Long providerId) {
		Map<ProviderJob.Status, Long> counts = new EnumMap<>(ProviderJob.Status.class);
		jdbc.sql("select status, count(*) as n from report_provider_job where provider_id = ? group by status")
				.param(providerId)
				.query((rs, rowNum) -> counts.put(ProviderJob.Status.valueOf(rs.getString("status")), rs.getLong("n")))
				.list();
		return counts;
	}

	@Override
	public List<Long> findUnansweredBefore(LocalDateTime now) {
		return jdbc.sql("select id from report_provider_job where status = 'NEW' and respond_by < ? order by id")
				.param(now)
				.query(Long.class)
				.list();
	}

	@Override
	public Optional<KeyUse> findKey(Long providerId, String key) {
		return jdbc.sql("""
				select job_id, action from report_provider_job_key where provider_id = ? and idempotency_key = ?
				""")
				.params(providerId, key)
				.query((rs, rowNum) -> new KeyUse(rs.getLong("job_id"), Action.valueOf(rs.getString("action"))))
				.optional();
	}

	@Override
	public void keepKey(Long providerId, String key, KeyUse use, LocalDateTime at) {
		jdbc.sql("""
				insert into report_provider_job_key (provider_id, idempotency_key, job_id, action, created_at)
				values (?, ?, ?, ?, ?)
				""")
				.params(providerId, key, use.jobId(), use.action().name(), at)
				.update();
	}

	@Override
	public int forgetKeysBefore(LocalDateTime cutoff) {
		return jdbc.sql("delete from report_provider_job_key where created_at < ?").param(cutoff).update();
	}

	private static String statusFilter(Set<ProviderJob.Status> statuses) {
		return statuses.isEmpty() ? "" : " and status in (:statuses)";
	}

	/** A list the {@code in} clause can take; never empty, as MySQL refuses {@code in ()}. */
	private static List<String> names(Set<ProviderJob.Status> statuses) {
		return statuses.isEmpty() ? List.of("") : statuses.stream().map(Enum::name).collect(Collectors.toList());
	}

	private ProviderJob row(ResultSet rs, int rowNum) throws SQLException {
		String declineReason = rs.getString("decline_reason");
		LocalDateTime completedAt = time(rs, "completed_at");
		return new ProviderJob(
				rs.getLong("id"),
				rs.getLong("provider_id"),
				rs.getLong("value_added_service_request_id"),
				rs.getString("service_name"),
				rs.getString("district"),
				rs.getString("area_name"),
				rs.getString("elder_summary"),
				rs.getString("languages"),
				rs.getString("booked_by"),
				time(rs, "scheduled_start"),
				rs.getInt("duration_minutes"),
				ProviderJob.Status.valueOf(rs.getString("status")),
				time(rs, "respond_by"),
				time(rs, "dispatched_at"),
				time(rs, "accepted_at"),
				declineReason == null ? null : new ProviderJob.Decline(
						ProviderJob.DeclineReason.valueOf(declineReason), rs.getString("decline_note"),
						time(rs, "declined_at")),
				completedAt == null ? null : new ProviderJob.Completion(time(rs, "actual_start"),
						time(rs, "actual_end"), rs.getString("completion_note"), completedAt),
				time(rs, "cancelled_at"),
				rs.getString("cancel_reason"),
				rs.getInt("version"));
	}

	private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, LocalDateTime.class);
	}
}
