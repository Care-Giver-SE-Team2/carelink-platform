package sg.nus.carelink.profile.domain.service;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import sg.nus.carelink.profile.domain.model.Credential;

class CredentialResubmissionHistoryTest {
    private Credential row(long id, long caregiver, long type, Credential.Status status, Long parent) {
        return new Credential(id, caregiver, type, null, "Demo", "Demo", LocalDate.of(2026,1,1),
                LocalDate.of(2026,10,15), status, null, null, parent);
    }
    @Test void explicitRetryRemovesOnlyRejectedAttemptAndRetainsOldExpiryReminder() {
        var old = row(1,1,1,Credential.Status.PUBLISHED,null);
        var rejected = row(2,1,1,Credential.Status.REJECTED,1L);
        var next = row(3,1,1,Credential.Status.SUBMITTED,1L);
        var input = List.of(old,rejected,next);
        var graph = CredentialResubmissionHistory.forAlerts(input,Map.of(3L,2L));
        assertThat(graph).containsExactly(old,next);
        var evaluated = new CaregiverCredentialAlertPolicy().evaluate(1L,graph,LocalDate.of(2026,10,11),30);
        assertThat(evaluated.reviewRequired()).isFalse();
        assertThat(evaluated.alerts()).hasSize(1);
        assertThat(evaluated.alerts().getFirst().renewalState()).isEqualTo(CaregiverCredentialAlertPolicy.RenewalState.PENDING_REVIEW);
        assertThat(input).hasSize(3);
    }
    @Test void invalidLinksNeverHideHistory() {
        var rejected = row(1,1,1,Credential.Status.REJECTED,9L);
        for (var next : List.of(row(2,2,1,Credential.Status.SUBMITTED,9L),row(2,1,2,Credential.Status.SUBMITTED,9L),
                row(2,1,1,Credential.Status.SUBMITTED,8L))) {
            assertThat(CredentialResubmissionHistory.forAlerts(List.of(rejected,next),Map.of(2L,1L))).hasSize(2);
        }
        assertThat(CredentialResubmissionHistory.forAlerts(List.of(rejected),Map.of(2L,1L))).hasSize(1);
    }
    @Test void cyclesAndNonRejectedParentsRemainVisible() {
        var a = row(1,1,1,Credential.Status.REJECTED,null);
        var b = row(2,1,1,Credential.Status.REJECTED,null);
        assertThat(CredentialResubmissionHistory.forAlerts(List.of(a,b),Map.of(1L,2L,2L,1L))).hasSize(2);
        var pending = row(3,1,1,Credential.Status.SUBMITTED,null);
        assertThat(CredentialResubmissionHistory.forAlerts(List.of(pending,b),Map.of(2L,3L))).hasSize(2);
    }
    @Test void digestIsStableAndContentSensitive() {
        assertThat(CredentialDigest.sha256(new byte[]{1})).hasSize(64).isEqualTo(CredentialDigest.sha256(new byte[]{1}))
                .isNotEqualTo(CredentialDigest.sha256(new byte[]{2}));
    }
}
