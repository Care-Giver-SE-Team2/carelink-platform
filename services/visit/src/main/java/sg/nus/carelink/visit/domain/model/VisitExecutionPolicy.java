package sg.nus.carelink.visit.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/** All thresholds are supplied by configuration; callers use the Singapore business clock. */
public record VisitExecutionPolicy(Duration earlyArrival, Duration lateThreshold) {
    public LocalDateTime opens(Visit v) { return v.scheduledStart().minus(earlyArrival); }
    public LocalDateTime closes(Visit v) { return v.scheduledEnd() == null ? v.scheduledStart().toLocalDate().atTime(LocalTime.MAX) : v.scheduledEnd(); }
    public boolean inWindow(Visit v, LocalDateTime now) { return !now.isBefore(opens(v)) && !now.isAfter(closes(v)); }
    public boolean late(Visit v, LocalDateTime now) { return now.isAfter(v.scheduledStart().plus(lateThreshold)); }
    public void requireWindow(Visit v, LocalDateTime now) {
        if (!inWindow(v, now)) throw new BusinessRuleViolation("VISIT_CHECK_IN_WINDOW", "Check-in is outside the allowed time window.");
    }
}
