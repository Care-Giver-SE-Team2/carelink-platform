package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.nus.carelink.visit.domain.model.MissedCheckInTrigger;
import sg.nus.carelink.visit.domain.repository.MissedCheckInRepository;

@Repository
class JdbcMissedCheckInRepository implements MissedCheckInRepository {
    private static final String CANDIDATES = """
            SELECT v.id, v.scheduled_start FROM visit v
            WHERE v.status='SCHEDULED' AND v.caregiver_id IS NOT NULL AND v.checked_in_at IS NULL
            AND v.scheduled_start >= ? AND v.scheduled_start < ?
            AND NOT EXISTS (SELECT 1 FROM visit_missed_check_in_trigger t WHERE t.visit_id=v.id)
            """;
    private static final String FIRST_PAGE = CANDIDATES + " ORDER BY v.scheduled_start, v.id LIMIT ?";
    private static final String NEXT_PAGE = CANDIDATES
            + " AND (v.scheduled_start > ? OR (v.scheduled_start = ? AND v.id > ?)) ORDER BY v.scheduled_start, v.id LIMIT ?";
    private final JdbcTemplate jdbc;
    JdbcMissedCheckInRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public List<Candidate> candidates(LocalDateTime since, LocalDateTime before, Candidate after, int limit) {
        if (after == null) return jdbc.query(FIRST_PAGE, JdbcMissedCheckInRepository::candidate,
                Timestamp.valueOf(since), Timestamp.valueOf(before), limit);
        return jdbc.query(NEXT_PAGE, JdbcMissedCheckInRepository::candidate,
                Timestamp.valueOf(since), Timestamp.valueOf(before), Timestamp.valueOf(after.scheduledStart()),
                Timestamp.valueOf(after.scheduledStart()), after.visitId(), limit);
    }
    private static Candidate candidate(java.sql.ResultSet row, int ignored) throws java.sql.SQLException {
        // Match main's Hibernate Visit binding AND reading convention.
        return new Candidate(row.getLong("id"), row.getTimestamp("scheduled_start").toLocalDateTime());
    }
    @Override public boolean exists(Long visitId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM visit_missed_check_in_trigger WHERE visit_id=?)", Boolean.class, visitId));
    }
    @Override public void save(MissedCheckInTrigger t) {
        jdbc.update("""
                INSERT INTO visit_missed_check_in_trigger(visit_id,incident_id,triggered_caregiver_id,
                scheduled_start,check_in_due_at,observed_visit_version,triggered_at) VALUES (?,?,?,?,?,?,?)
                """, t.visitId(), t.incidentId(), t.caregiverId(), Timestamp.valueOf(t.scheduledStart()),
                Timestamp.valueOf(t.dueAt()), t.observedVersion(), Timestamp.valueOf(t.triggeredAt()));
    }
    @Override public Optional<MissedCheckInTrigger> find(Long visitId) {
        return jdbc.query("SELECT * FROM visit_missed_check_in_trigger WHERE visit_id=?", (row, ignored) ->
                new MissedCheckInTrigger(row.getLong("visit_id"), row.getLong("incident_id"),
                        row.getLong("triggered_caregiver_id"), row.getTimestamp("scheduled_start").toLocalDateTime(),
                        row.getTimestamp("check_in_due_at").toLocalDateTime(), row.getInt("observed_visit_version"),
                        row.getTimestamp("triggered_at").toLocalDateTime()), visitId).stream().findFirst();
    }
}
