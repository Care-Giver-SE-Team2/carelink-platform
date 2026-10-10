package sg.nus.carelink.visit.domain.model;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaregiverCommandRulesTest {
    private final LocalDateTime now = LocalDateTime.of(2026,10,7,12,0);
    private Visit visit(Visit.Status status) { return new Visit(1L,2L,3L,4L,null,"A",now.minusMinutes(5),now.plusHours(1),
            null,null,status,null,5L,0,null,null); }
    @Test void openReportsPauseAndFinalReportsKeepTheirState() {
        for (var state : Visit.Status.values()) {
            if (state == Visit.Status.CANCELLED) assertThatThrownBy(() -> visit(state).reportedException(now)).hasMessageContaining("cannot");
            else assertThat(visit(state).reportedException(now).status()).isEqualTo(switch (state) {
                case SCHEDULED, ARRIVED, IN_PROGRESS -> Visit.Status.EXCEPTION;
                default -> state;
            });
        }
        assertThatThrownBy(() -> visit(Visit.Status.SCHEDULED).reportedException(now.minusHours(1))).hasMessageContaining("cannot");
    }
    @Test void receiptRejectsAnyChangedActionVisitOrPayload() {
        var receipt = new CaregiverCommandReceipt(1L,UUID.randomUUID(),"REPORT_INCIDENT",2L,"hash",3L,1,now);
        receipt.requireSame("REPORT_INCIDENT",2L,"hash");
        assertThatThrownBy(() -> receipt.requireSame("CHECK_IN",2L,"hash")).hasMessageContaining("different content");
        assertThatThrownBy(() -> receipt.requireSame("REPORT_INCIDENT",4L,"hash")).hasMessageContaining("different content");
        assertThatThrownBy(() -> receipt.requireSame("REPORT_INCIDENT",2L,"other")).hasMessageContaining("different content");
    }
    @Test void fingerprintsAreStableAndUnambiguous() {
        assertThat(CommandFingerprint.of(1,"fact",null)).isEqualTo(CommandFingerprint.of(1,"fact",null)).hasSize(64);
        assertThat(CommandFingerprint.of("ab","c")).isNotEqualTo(CommandFingerprint.of("a","bc"));
        assertThat(CommandFingerprint.of("fact")).doesNotContain("fact");
    }
}
