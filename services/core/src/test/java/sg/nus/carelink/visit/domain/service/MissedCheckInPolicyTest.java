package sg.nus.carelink.visit.domain.service;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import sg.nus.carelink.visit.domain.model.Visit;

class MissedCheckInPolicyTest {
    private final LocalDateTime start = LocalDateTime.of(2026, 10, 8, 10, 0);
    private final MissedCheckInPolicy policy = new MissedCheckInPolicy(Duration.ofMinutes(10), Duration.ofHours(24));
    private Visit visit(Long caregiver, Visit.Status status, LocalDateTime checkedIn) {
        return new Visit(1L, 2L, caregiver, null, null, "Care", start, start.plusHours(1), checkedIn, null,
                status, start.plusHours(2), null, 0, null, null);
    }
    @Test void strictThresholdAndInclusiveLookbackWithNoDependenceOnEndTime() {
        var v = visit(3L, Visit.Status.SCHEDULED, null);
        assertThat(policy.dueAt(v)).isEqualTo(start.plusMinutes(10));
        assertThat(policy.eligible(v, start.plusMinutes(9))).isFalse();
        assertThat(policy.eligible(v, start.plusMinutes(10))).isFalse();
        assertThat(policy.eligible(v, start.plusMinutes(10).plusNanos(1))).isTrue();
        assertThat(policy.eligible(v, start.plusHours(24))).isTrue();
        assertThat(policy.eligible(v, start.plusHours(24).plusNanos(1))).isFalse();
    }
    @ParameterizedTest @EnumSource(Visit.Status.class)
    void onlyUnstartedAssignedVisitsAreEligible(Visit.Status status) {
        assertThat(policy.eligible(visit(3L, status, null), start.plusMinutes(11))).isEqualTo(status == Visit.Status.SCHEDULED);
    }
    @Test void nullAssignmentStartOrPriorCheckInSkips() {
        assertThat(policy.eligible(visit(null, Visit.Status.SCHEDULED, null), start.plusMinutes(11))).isFalse();
        assertThat(policy.eligible(visit(3L, Visit.Status.SCHEDULED, start), start.plusMinutes(11))).isFalse();
        var v = new Visit(1L, 2L, 3L, null, null, null, null, null, null, null, Visit.Status.SCHEDULED, null, null, 0, null, null);
        assertThat(policy.eligible(v, start)).isFalse();
    }
    @Test void invalidConfigurationFailsAndZeroLatenessIsValid() {
        var day=Duration.ofDays(1);
        var negative=Duration.ofSeconds(-1);
        assertThatThrownBy(() -> new MissedCheckInPolicy(null, day)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MissedCheckInPolicy(Duration.ZERO, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MissedCheckInPolicy(negative, day)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MissedCheckInPolicy(Duration.ZERO, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MissedCheckInPolicy(Duration.ZERO, negative)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new MissedCheckInPolicy(Duration.ZERO, Duration.ofDays(1)).eligible(visit(3L, Visit.Status.SCHEDULED, null), start.plusNanos(1))).isTrue();
    }
}
