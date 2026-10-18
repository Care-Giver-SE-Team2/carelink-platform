package sg.nus.carelink.report.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.domain.repository.ReportFactsSource;

/** Emits one ReportRequested v1 outbox event per elder for a completed Singapore week. */
@Service
public class ReportTaskProducer {
    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
    private final ReportFactsSource facts;
    private final Events events;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    public ReportTaskProducer(ReportFactsSource facts, Events events, Clock clock, JdbcTemplate jdbc) {
        this.facts = facts;
        this.events = events;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    /** Runs on Monday or later; never publishes an unfinished Sunday. */
    @Transactional
    public int publishLastCompletedWeek() {
        LocalDate today = LocalDate.now(clock.withZone(SINGAPORE));
        ReportPeriod period = ReportPeriod.weekEndingOnOrBefore(today.minusDays(1));
        return publishPeriod(period);
    }

    /** Explicit backfill entry point; must only be called for a completed Monday-Sunday week. */
    @Transactional
    public int publishPeriod(ReportPeriod period) {
        Objects.requireNonNull(period, "period");
        LocalDate today = LocalDate.now(clock.withZone(SINGAPORE));
        if (!java.time.DayOfWeek.MONDAY.equals(period.start().getDayOfWeek())
                || !period.end().equals(period.start().plusDays(6))
                || !period.end().isBefore(today)) {
            throw new IllegalArgumentException("ReportRequested requires a completed Monday-Sunday week");
        }
        List<Long> elderIds = facts.eldersWithVisitsIn(period);
        if (elderIds == null) throw new IllegalStateException("No elder list returned");
        String runId = "weekly-" + period.start().toString().replace("-", "");
        int count = 0;
        for (Long elderId : new LinkedHashSet<>(elderIds)) {
            if (elderId == null || elderId <= 0) {
                throw new IllegalArgumentException("Invalid elderId returned by ReportFactsSource");
            }
            // MySQL INSERT IGNORE serializes competing claims on the unique business key.
            // The claim and Events.publish share this transaction: both commit or both roll back.
            int claimed = jdbc.update("INSERT IGNORE INTO report_task_dispatch "
                    + "(elder_id, period_start, period_end) VALUES (?, ?, ?)",
                    elderId, java.sql.Date.valueOf(period.start()), java.sql.Date.valueOf(period.end()));
            if (claimed == 0) continue;
            events.publish("ReportRequested", Map.of(
                    "schemaVersion", 1,
                    "elderId", elderId,
                    "periodStart", period.start().toString(),
                    "periodEnd", period.end().toString(),
                    "runId", runId));
            count++;
        }
        return count;
    }
}
