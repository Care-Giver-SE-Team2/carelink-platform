package sg.nus.carelink.profile.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import sg.nus.carelink.profile.domain.model.Credential;
import sg.nus.carelink.profile.domain.service.CaregiverCredentialAlertPolicy.RenewalState;

class CaregiverCredentialAlertPolicyTest {
    private final LocalDate today = LocalDate.of(2026, 9, 25);
    private final CaregiverCredentialAlertPolicy policy = new CaregiverCredentialAlertPolicy();

    private Credential certificate(long id, Credential.Status status, LocalDate from, LocalDate expiry, Long renews) {
        return certificate(id, 1L, 2L, status, from, expiry, renews);
    }

    private Credential certificate(long id, long owner, long type, Credential.Status status,
            LocalDate from, LocalDate expiry, Long renews) {
        return new Credential(id, owner, type, null, "CERT-" + id, "Issuer", from, expiry, status, null, null, renews);
    }

    private Credential old() {
        return certificate(1, Credential.Status.PUBLISHED, null, today.minusDays(1), null);
    }

    private CaregiverCredentialAlertPolicy.Evaluation evaluate(Credential... rows) {
        return policy.evaluate(1L, List.of(rows), today, 30);
    }

    @ParameterizedTest
    @CsvSource({"-1,EXPIRED", "0,EXPIRING", "1,EXPIRING", "30,EXPIRING"})
    void inclusiveDateBoundaries(int days, String warning) {
        var result = evaluate(certificate(1, Credential.Status.PUBLISHED, null, today.plusDays(days), null));
        assertThat(result.alerts()).hasSize(1);
        assertThat(result.alerts().getFirst().warning()).isEqualTo(warning);
        assertThat(result.alerts().getFirst().daysUntilExpiry()).isEqualTo(days);
        assertThat(result.alerts().getFirst().renewalState()).isEqualTo(RenewalState.NONE);
        assertThat(result.reviewRequired()).isFalse();
    }

    @Test
    void outsideWindowPermanentAndFutureStartDoNotProduceExpiryAlerts() {
        assertThat(evaluate(
                certificate(1, Credential.Status.PUBLISHED, null, today.plusDays(31), null),
                certificate(2, Credential.Status.PUBLISHED, null, LocalDate.of(9999, 12, 31), null),
                certificate(3, Credential.Status.PUBLISHED, today.plusDays(1), today.plusDays(5), null)).alerts()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Credential.Status.class, names = {"SUBMITTED", "REJECTED", "REVOKED"})
    void unapprovedAndRevokedRecordsAreNotExpiryAlerts(Credential.Status status) {
        assertThat(evaluate(certificate(1, status, null, today.minusDays(1), null)).alerts()).isEmpty();
    }

    @Test
    void zeroWindowAndNegativeConfiguration() {
        var rows = List.of(old(), certificate(2, Credential.Status.PUBLISHED, null, today, null),
                certificate(3, Credential.Status.PUBLISHED, null, today.plusDays(1), null));
        assertThat(policy.evaluate(1L, rows, today, 0).alerts()).extracting(a -> a.credential().id()).containsExactly(1L, 2L);
        assertThatThrownBy(() -> policy.evaluate(1L, rows, today, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"SUBMITTED,PENDING_REVIEW", "REJECTED,REJECTED", "REVOKED,REVOKED"})
    void unsuccessfulRenewalDoesNotClearOldWarning(Credential.Status status, RenewalState expected) {
        var result = evaluate(old(), certificate(2, status, null, today.plusDays(365), 1L));
        assertThat(result.alerts()).hasSize(1);
        assertThat(result.alerts().getFirst().renewalState()).isEqualTo(expected);
        assertThat(result.alerts().getFirst().renewalValidFrom()).isNull();
        assertThat(result.reviewRequired()).isFalse();
    }

    @Test
    void approvedFutureRenewalRetainsOldWarningUntilItsInclusiveStartDate() {
        var start = today.plusDays(7);
        var next = certificate(2, Credential.Status.PUBLISHED, start, today.plusDays(365), 1L);
        var alert = evaluate(old(), next).alerts().getFirst();
        assertThat(alert.renewalState()).isEqualTo(RenewalState.APPROVED_NOT_EFFECTIVE);
        assertThat(alert.renewalValidFrom()).isEqualTo(start);
        assertThat(policy.evaluate(1L, List.of(old(), next), start, 30).alerts()).isEmpty();
    }

    @Test
    void nullOrCurrentStartOnApprovedRenewalClearsOldWarning() {
        for (LocalDate from : new LocalDate[] { null, today, today.minusDays(1) }) {
            var next = certificate(2, Credential.Status.PUBLISHED, from, today.plusDays(365), 1L);
            var result = evaluate(old(), next);
            assertThat(result.alerts()).isEmpty();
            assertThat(result.reviewRequired()).isFalse();
        }
    }

    @Test
    void overdueReplacementIsWarnedInsteadOfItsAncestors() {
        var second = certificate(2, Credential.Status.EXPIRED, null, today.minusDays(2), 1L);
        var third = certificate(3, Credential.Status.EXPIRING, null, today.plusDays(3), 2L);
        assertThat(evaluate(old(), second).alerts()).extracting(a -> a.credential().id()).containsExactly(2L);
        assertThat(evaluate(old(), second, third).alerts()).extracting(a -> a.credential().id()).containsExactly(3L);
    }

    @Test
    void pendingRenewalOfAnEffectiveReplacementDoesNotResurrectAncestors() {
        var second = certificate(2, Credential.Status.PUBLISHED, null, today.plusDays(3), 1L);
        var third = certificate(3, Credential.Status.SUBMITTED, null, today.plusDays(365), 2L);
        var result = evaluate(old(), second, third);
        assertThat(result.alerts()).extracting(a -> a.credential().id()).containsExactly(2L);
        assertThat(result.alerts().getFirst().renewalState()).isEqualTo(RenewalState.PENDING_REVIEW);
    }

    @Test
    void noLinkMeansNoReplacementAndSortingDoesNotDependOnRepositoryOrder() {
        var later = certificate(9, Credential.Status.EXPIRING, null, today.plusDays(1), null);
        var earlierId = certificate(3, Credential.Status.EXPIRING, null, today.plusDays(1), null);
        assertThat(evaluate(later, old(), earlierId).alerts()).extracting(a -> a.credential().id()).containsExactly(1L, 3L, 9L);
    }

    @Test
    void anotherCaregiversCertificateCannotSuppressOrLeakIntoTheResult() {
        var foreign = certificate(2, 99, 2, Credential.Status.PUBLISHED, null, today.plusDays(365), 1L);
        assertThat(evaluate(old(), foreign).alerts()).extracting(a -> a.credential().id()).containsExactly(1L);
        var ownWithForeignParent = certificate(3, Credential.Status.PUBLISHED, null, today.plusDays(2), 2L);
        var result = evaluate(foreign, ownWithForeignParent);
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.alerts()).extracting(a -> a.credential().id()).containsExactly(3L);
        assertThat(result.alerts().getFirst().renewalState()).isEqualTo(RenewalState.CHECK_REQUIRED);
    }

    @Test
    void wrongTypeAndMissingParentsFailConservatively() {
        var wrongType = certificate(2, 1, 99, Credential.Status.PUBLISHED, null, today.plusDays(365), 1L);
        var orphan = certificate(3, Credential.Status.PUBLISHED, null, today.plusDays(365), 900L);
        var result = evaluate(old(), wrongType, orphan);
        assertThat(result.alerts()).extracting(a -> a.credential().id()).containsExactly(1L);
        assertThat(result.alerts().getFirst().renewalState()).isEqualTo(RenewalState.CHECK_REQUIRED);
        assertThat(result.reviewRequired()).isTrue();
    }

    @Test
    @Timeout(2)
    void cyclesAndSelfLinksDoNotLoopOrHideAlerts() {
        var a = certificate(1, Credential.Status.PUBLISHED, null, today.minusDays(2), 2L);
        var b = certificate(2, Credential.Status.PUBLISHED, null, today.minusDays(1), 1L);
        var self = certificate(3, Credential.Status.PUBLISHED, null, today, 3L);
        var result = evaluate(a, b, self);
        assertThat(result.alerts()).hasSize(3).allMatch(alert -> alert.renewalState() == RenewalState.CHECK_REQUIRED);
        assertThat(result.reviewRequired()).isTrue();
    }

    @Test
    void branchingRelationsKeepAllRelevantAlertsAndDoNotPickAnArbitraryLatestRow() {
        var a = certificate(2, Credential.Status.PUBLISHED, null, today.plusDays(365), 1L);
        var b = certificate(3, Credential.Status.SUBMITTED, null, today.plusDays(365), 1L);
        var result = evaluate(a, old(), b);
        assertThat(result.alerts()).hasSize(1);
        assertThat(result.alerts().getFirst().renewalState()).isEqualTo(RenewalState.CHECK_REQUIRED);
    }

    @Test
    void contradictoryDatesAndMissingDataRequireReviewWithoutClearingAncestors() {
        var invalid = certificate(2, Credential.Status.PUBLISHED, today.plusDays(30), today.plusDays(5), 1L);
        var expiredInFuture = certificate(3, Credential.Status.EXPIRED, null, today.plusDays(365), null);
        var missingExpiry = certificate(4, Credential.Status.PUBLISHED, null, null, null);
        var missingStatus = certificate(5, null, null, today, null);
        var result = evaluate(old(), invalid, expiredInFuture, missingExpiry, missingStatus);
        assertThat(result.reviewRequired()).isTrue();
        assertThat(result.alerts()).extracting(a -> a.credential().id()).containsExactly(1L, 2L);
        assertThat(result.alerts()).allMatch(a -> a.renewalState() == RenewalState.CHECK_REQUIRED);
    }

    @Test
    void approvedChildOfUnapprovedOrFutureParentRequiresReview() {
        var pending = certificate(1, Credential.Status.SUBMITTED, null, today.minusDays(1), null);
        var child = certificate(2, Credential.Status.PUBLISHED, null, today.plusDays(1), 1L);
        assertThat(evaluate(pending, child).reviewRequired()).isTrue();
        var future = certificate(1, Credential.Status.PUBLISHED, today.plusDays(1), today.plusDays(5), null);
        assertThat(evaluate(future, child).reviewRequired()).isTrue();
    }

    @Test
    void emptyInputIsNotAnApprovalAssertion() {
        var result = evaluate();
        assertThat(result.alerts()).isEmpty();
        assertThat(result.reviewRequired()).isFalse();
    }
}
