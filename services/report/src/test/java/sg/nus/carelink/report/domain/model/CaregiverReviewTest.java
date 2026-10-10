package sg.nus.carelink.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

class CaregiverReviewTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);

    @Test
    void submitsAReviewAndNormalisesFeedback() {
        CaregiverReview review = CaregiverReview.submit(
                11L, 21L, 31L, START, END,
                (byte) 5, (byte) 4, (byte) 5,
                "  Very patient and professional.  ",
                CaregiverReview.RenewalDecision.RENEW_CURRENT);

        assertThat(review.id()).isNull();
        assertThat(review.familyMemberId()).isEqualTo(11L);
        assertThat(review.elderId()).isEqualTo(21L);
        assertThat(review.caregiverId()).isEqualTo(31L);
        assertThat(review.periodStart()).isEqualTo(START);
        assertThat(review.periodEnd()).isEqualTo(END);
        assertThat(review.overallRating()).isEqualTo((byte) 5);
        assertThat(review.punctualityScore()).isEqualTo((byte) 4);
        assertThat(review.careQualityScore()).isEqualTo((byte) 5);
        assertThat(review.feedbackNotes()).isEqualTo("Very patient and professional.");
        assertThat(review.renewalDecision()).isEqualTo(CaregiverReview.RenewalDecision.RENEW_CURRENT);
        assertThat(review.createdAt()).isNull();
    }

    @Test
    void optionalScoresAndBlankFeedbackMayBeOmitted() {
        CaregiverReview review = CaregiverReview.submit(
                11L, 21L, 31L, START, END,
                (byte) 4, null, null, "   ",
                CaregiverReview.RenewalDecision.REQUEST_CHANGE);

        assertThat(review.punctualityScore()).isNull();
        assertThat(review.careQualityScore()).isNull();
        assertThat(review.feedbackNotes()).isNull();
    }

    @Test
    void rejectsAnEndDateBeforeTheStartDate() {
        assertThatThrownBy(() -> CaregiverReview.submit(
                11L, 21L, 31L, END, START,
                (byte) 5, null, null, null,
                CaregiverReview.RenewalDecision.RENEW_CURRENT))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting(error -> ((BusinessRuleViolation) error).code())
                .isEqualTo("CAREGIVER_REVIEW_PERIOD_INVALID");
    }

    @Test
    void rejectsScoresOutsideOneToFive() {
        assertInvalidScore((byte) 0, (byte) 4, (byte) 4);
        assertInvalidScore((byte) 6, (byte) 4, (byte) 4);
        assertInvalidScore((byte) 5, (byte) 0, (byte) 4);
        assertInvalidScore((byte) 5, (byte) 4, (byte) 6);
    }

    @Test
    void requiresCoreIdentifiersPeriodRatingAndDecision() {
        assertThatThrownBy(() -> CaregiverReview.submit(null, 21L, 31L, START, END, (byte) 5, null, null, null, CaregiverReview.RenewalDecision.RENEW_CURRENT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CaregiverReview.submit(11L, null, 31L, START, END, (byte) 5, null, null, null, CaregiverReview.RenewalDecision.RENEW_CURRENT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CaregiverReview.submit(11L, 21L, null, START, END, (byte) 5, null, null, null, CaregiverReview.RenewalDecision.RENEW_CURRENT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CaregiverReview.submit(11L, 21L, 31L, null, END, (byte) 5, null, null, null, CaregiverReview.RenewalDecision.RENEW_CURRENT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CaregiverReview.submit(11L, 21L, 31L, START, null, (byte) 5, null, null, null, CaregiverReview.RenewalDecision.RENEW_CURRENT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CaregiverReview.submit(11L, 21L, 31L, START, END, null, null, null, null, CaregiverReview.RenewalDecision.RENEW_CURRENT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CaregiverReview.submit(11L, 21L, 31L, START, END, (byte) 5, null, null, null, null)).isInstanceOf(NullPointerException.class);
    }

    private static void assertInvalidScore(Byte overall, Byte punctuality, Byte quality) {
        assertThatThrownBy(() -> CaregiverReview.submit(
                11L, 21L, 31L, START, END,
                overall, punctuality, quality, null,
                CaregiverReview.RenewalDecision.RENEW_CURRENT))
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting(error -> ((BusinessRuleViolation) error).code())
                .isEqualTo("CAREGIVER_REVIEW_SCORE_INVALID");
    }
}
