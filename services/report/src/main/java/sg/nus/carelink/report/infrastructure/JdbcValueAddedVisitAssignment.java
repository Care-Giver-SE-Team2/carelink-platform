package sg.nus.carelink.report.infrastructure;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.report.application.ValueAddedVisitAssignment;

/**
 * Uses the established primary-caregiver assignment, from core, excluding leave and overlapping
 * visits. The leave and the overlap are still read with SQL from core's and visit's tables; they
 * become CoreApi.onLeave and VisitApi.caregiverBusy before the schema split.
 */
@Component
public class JdbcValueAddedVisitAssignment implements ValueAddedVisitAssignment {
    private final CoreApi core;
    private final JdbcClient jdbc;

    public JdbcValueAddedVisitAssignment(CoreApi core, JdbcClient jdbc) {
        this.core = core;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Long> chooseCaregiver(Long elderId, LocalDateTime start, LocalDateTime end) {
        Optional<Long> candidate = core.findPrimaryCaregiverId(elderId);
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
