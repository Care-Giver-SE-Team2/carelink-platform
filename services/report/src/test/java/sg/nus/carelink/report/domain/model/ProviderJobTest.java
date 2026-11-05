package sg.nus.carelink.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

class ProviderJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 9, 0);

    private static ProviderJob dispatched(LocalDateTime start) {
        return new ProviderJob(24081L, 509L, 812L, "Hospital escort", "19", "Hougang", "Male, 81", "Hokkien",
                "Family member", start, 180, ProviderJob.Status.NEW, ProviderJob.respondByFor(start, NOW), NOW, null,
                null, null, null, null, 0);
    }

    @Test
    void theProviderHasADayToAnswerButNoLaterThanFourHoursBeforeTheStart() {
        assertThat(ProviderJob.respondByFor(NOW.plusDays(3), NOW)).isEqualTo(NOW.plusHours(24));
        assertThat(ProviderJob.respondByFor(NOW.plusHours(10), NOW)).isEqualTo(NOW.plusHours(6));
    }

    @Test
    void aRequestLessThanFiveHoursAwayIsNotOffered() {
        assertThat(ProviderJob.offerable(NOW.plusHours(5), NOW)).isTrue();
        assertThat(ProviderJob.offerable(NOW.plusHours(5).minusMinutes(1), NOW)).isFalse();
        assertThatThrownBy(() -> ProviderJob.dispatched(509L, 812L, "Hospital escort", "19", "Hougang", "Male, 81",
                "Hokkien", "Family member", NOW.plusHours(4), 180, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyChangeRaisesTheVersionAndOnlyANewJobShowsItsDeadline() {
        ProviderJob job = dispatched(NOW.plusDays(2));
        assertThat(job.respondByShown()).isEqualTo(NOW.plusHours(24));

        ProviderJob accepted = job.accepted(NOW.plusHours(1));
        ProviderJob completed = accepted.completed(
                new ProviderJob.Completion(NOW.plusHours(2), NOW.plusHours(5), null, NOW.plusHours(6)));

        assertThat(accepted.version()).isEqualTo(1);
        assertThat(accepted.respondByShown()).isNull();
        assertThat(completed.status()).isEqualTo(ProviderJob.Status.COMPLETED);
        assertThat(completed.version()).isEqualTo(2);
        assertThat(completed.acceptedAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    void aCancelledJobIsRefusedWithCareLinksReason() {
        ProviderJob cancelled = dispatched(NOW.plusDays(2)).cancelled("The elder withdrew it.", NOW);

        assertThatThrownBy(() -> cancelled.accepted(NOW))
                .isInstanceOfSatisfying(BusinessRuleViolation.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo("JOB_CANCELLED");
                    assertThat(refusal.getMessage()).isEqualTo("The elder withdrew it.");
                });
        assertThatThrownBy(() -> cancelled.cancelled("again", NOW))
                .isInstanceOfSatisfying(BusinessRuleViolation.class,
                        refusal -> assertThat(refusal.code()).isEqualTo("JOB_NOT_ACTIONABLE"));
    }

    @Test
    void onlyANewJobCanBeAnsweredAndOnlyBeforeItsDeadline() {
        ProviderJob job = dispatched(NOW.plusDays(2));

        assertThatThrownBy(() -> job.accepted(NOW.plusHours(24).plusSeconds(1)))
                .isInstanceOfSatisfying(BusinessRuleViolation.class,
                        refusal -> assertThat(refusal.code()).isEqualTo("RESPONSE_DEADLINE_PASSED"));
        ProviderJob accepted = job.accepted(NOW);
        assertThatThrownBy(() -> accepted.declined(new ProviderJob.Decline(ProviderJob.DeclineReason.OUTSIDE_AREA,
                null, NOW))).isInstanceOfSatisfying(BusinessRuleViolation.class,
                        refusal -> assertThat(refusal.code()).isEqualTo("JOB_NOT_ACTIONABLE"));
        assertThatThrownBy(() -> job.completed(new ProviderJob.Completion(NOW, NOW.plusHours(1), null, NOW.plusHours(2))))
                .isInstanceOfSatisfying(BusinessRuleViolation.class,
                        refusal -> assertThat(refusal.code()).isEqualTo("JOB_NOT_ACTIONABLE"));
    }

    @Test
    void aCompletionMustEndAfterItStartsAndNotInTheFuture() {
        ProviderJob accepted = dispatched(NOW.plusDays(2)).accepted(NOW);

        assertThatThrownBy(() -> accepted.completed(
                new ProviderJob.Completion(NOW.plusHours(1), NOW.plusHours(1), null, NOW.plusHours(2))))
                .isInstanceOf(ProviderJob.InvalidTimes.class);
        assertThatThrownBy(() -> accepted.completed(
                new ProviderJob.Completion(NOW, NOW.plusHours(3), null, NOW.plusHours(2))))
                .isInstanceOf(ProviderJob.InvalidTimes.class);
    }

    @Test
    void aDeclineForAnotherReasonNeedsANote() {
        assertThatThrownBy(() -> new ProviderJob.Decline(ProviderJob.DeclineReason.OTHER, " ", NOW))
                .isInstanceOf(ProviderJob.MissingNote.class);
        assertThat(new ProviderJob.Decline(ProviderJob.DeclineReason.OTHER, " Van broke down ", NOW).note())
                .isEqualTo("Van broke down");
    }

    @Test
    void anUnansweredJobExpiresOnlyAfterItsDeadline() {
        ProviderJob job = dispatched(NOW.plusDays(2));

        assertThatThrownBy(() -> job.expired(NOW.plusHours(24))).isInstanceOf(IllegalStateException.class);
        ProviderJob expired = job.expired(NOW.plusHours(24).plusSeconds(1));
        assertThat(expired.status()).isEqualTo(ProviderJob.Status.CANCELLED);
        assertThat(expired.cancelReason()).isEqualTo(ProviderJob.EXPIRED_REASON);
    }
}
