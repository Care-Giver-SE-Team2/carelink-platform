package sg.nus.carelink.report.infrastructure;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.profile.application.PrimaryCaregiverLookup;
import sg.nus.carelink.report.application.ValueAddedVisitAssignment;

/** Uses the established primary-caregiver assignment, excluding leave and overlapping visits. */
@Component
public class JdbcValueAddedVisitAssignment implements ValueAddedVisitAssignment {
    private final PrimaryCaregiverLookup primary;
    private final JdbcClient jdbc;

    public JdbcValueAddedVisitAssignment(PrimaryCaregiverLookup primary, JdbcClient jdbc) {
        this.primary = primary;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Long> chooseCaregiver(Long elderId, LocalDateTime start, LocalDateTime end) {
        Optional<Long> candidate = primary.findRosterableCaregiverId(elderId);
        if (candidate.isEmpty()) return Optional.empty();
        Long caregiverId = candidate.get();
        Long leave = jdbc.sql("""
                select count(*) from absence_report
                where caregiver_id = :caregiverId and status = 'APPROVED'
                  and start_date <= :day and end_date >= :day
                """).param("caregiverId", caregiverId).param("day", start.toLocalDate())
                .query(Long.class).single();
        if (leave > 0) return Optional.empty();
        Long overlaps = jdbc.sql("""
                select count(*) from visit
                where caregiver_id = :caregiverId
                  and status in ('SCHEDULED','ARRIVED','IN_PROGRESS')
                  and scheduled_start < :end
                  and coalesce(scheduled_end, date_add(scheduled_start, interval 1 hour)) > :start
                """).param("caregiverId", caregiverId).param("start", start).param("end", end)
                .query(Long.class).single();
        return overlaps == 0 ? candidate : Optional.empty();
    }
}
