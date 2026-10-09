package sg.nus.carelink.visit.domain.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import sg.nus.carelink.visit.domain.model.Visit;

/** SYS03 eligibility; an accepted alert transitions the locked Visit to EXCEPTION and closes check-in. */
public record MissedCheckInPolicy(Duration lateThreshold, Duration lookback) {
    public MissedCheckInPolicy {
        Objects.requireNonNull(lateThreshold, "lateThreshold");
        Objects.requireNonNull(lookback, "lookback");
        if (lateThreshold.isNegative() || lookback.isNegative() || lookback.isZero()) {
            throw new IllegalArgumentException("Late threshold must be nonnegative and lookback positive");
        }
    }
    public LocalDateTime dueAt(Visit visit) { return visit.scheduledStart().plus(lateThreshold); }
    public boolean eligible(Visit visit, LocalDateTime now) {
        return visit.caregiverId() != null && visit.status() == Visit.Status.SCHEDULED
                && visit.checkedInAt() == null && visit.scheduledStart() != null
                && !visit.scheduledStart().isBefore(now.minus(lookback)) && now.isAfter(dueAt(visit));
    }
}
