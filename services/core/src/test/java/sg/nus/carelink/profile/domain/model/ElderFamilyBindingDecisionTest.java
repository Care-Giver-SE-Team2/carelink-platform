package sg.nus.carelink.profile.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

class ElderFamilyBindingDecisionTest {
    private ElderFamilyBinding pending() {
        return ElderFamilyBinding.request(1L, 2L, ElderFamilyBinding.Relationship.SON,
                false, ElderFamilyBinding.AccessScope.FULL);
    }

    @Test void confirmActivatesAndSetsTimestamp() {
        var now = LocalDateTime.of(2026, 10, 9, 12, 0);
        var active = pending().confirm(now);
        assertEquals(ElderFamilyBinding.Status.ACTIVE, active.status());
        assertEquals(now, active.confirmedAt());
        assertTrue(active.allowsReadAt(now));
    }

    @Test void rejectDoesNotGrantAccess() {
        var rejected = pending().reject();
        assertEquals(ElderFamilyBinding.Status.REJECTED, rejected.status());
        assertFalse(rejected.allowsReadAt(LocalDateTime.now()));
    }

    @Test void decisionsCannotBeRepeated() {
        var now = LocalDateTime.now();
        assertThrows(BusinessRuleViolation.class, () -> pending().confirm(now).reject());
        assertThrows(BusinessRuleViolation.class, () -> pending().reject().confirm(now));
        assertThrows(BusinessRuleViolation.class, () -> pending().confirm(now).confirm(now));
    }
}
