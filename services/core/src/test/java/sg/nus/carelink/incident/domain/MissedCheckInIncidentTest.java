package sg.nus.carelink.incident.domain;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.incident.domain.model.Incident;

class MissedCheckInIncidentTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 8, 10, 11);
    @Test void assignedMissedCheckInHasExplicitSystemMeaning() {
        var i = Incident.raisedForMissedCheckIn(1L, 2L, now.minusMinutes(1), now);
        assertThat(i.source()).isEqualTo(Incident.Source.SYSTEM_MISSED_CHECKIN);
        assertThat(i.category()).isEqualTo(Incident.Category.SERVICE);
        assertThat(i.severity()).isEqualTo(Incident.Severity.MEDIUM);
        assertThat(i.reportedByUserId()).isNull();
        assertThat(i.description()).contains("Assigned caregiver", "2026-10-08T10:10");
        assertThat(i.status()).isEqualTo(Incident.Status.OPEN);
        assertThat(i.reportedAt()).isEqualTo(now);
    }
    @Test void mandatoryFactsCannotBeMissing() {
        assertThatNullPointerException().isThrownBy(() -> Incident.raisedForMissedCheckIn(null, 2L, now, now));
        assertThatNullPointerException().isThrownBy(() -> Incident.raisedForMissedCheckIn(1L, null, now, now));
        assertThatNullPointerException().isThrownBy(() -> Incident.raisedForMissedCheckIn(1L, 2L, null, now));
        assertThatNullPointerException().isThrownBy(() -> Incident.raisedForMissedCheckIn(1L, 2L, now, null));
    }
}
