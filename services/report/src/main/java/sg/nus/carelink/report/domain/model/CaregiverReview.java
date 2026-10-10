package sg.nus.carelink.report.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/** FM09 periodic caregiver review and renewal decision. */
public record CaregiverReview(
        Long id,
        Long familyMemberId,
        Long elderId,
        Long caregiverId,
        LocalDate periodStart,
        LocalDate periodEnd,
        Byte overallRating,
        Byte punctualityScore,
        Byte careQualityScore,
        String feedbackNotes,
        CaregiverReview.RenewalDecision renewalDecision,
        LocalDateTime createdAt) {

    public enum RenewalDecision {
        RENEW_CURRENT, REQUEST_CHANGE, CANCEL_SERVICE
    }

    public static CaregiverReview submit(
            Long familyMemberId,
            Long elderId,
            Long caregiverId,
            LocalDate periodStart,
            LocalDate periodEnd,
            Byte overallRating,
            Byte punctualityScore,
            Byte careQualityScore,
            String feedbackNotes,
            RenewalDecision renewalDecision) {

        Objects.requireNonNull(familyMemberId, "familyMemberId");
        Objects.requireNonNull(elderId, "elderId");
        Objects.requireNonNull(caregiverId, "caregiverId");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        Objects.requireNonNull(overallRating, "overallRating");
        Objects.requireNonNull(renewalDecision, "renewalDecision");

        if (periodEnd.isBefore(periodStart)) {
            throw new BusinessRuleViolation(
                    "CAREGIVER_REVIEW_PERIOD_INVALID",
                    "The review period end date cannot be before the start date");
        }

        requireScore("overall rating", overallRating);
        requireScore("punctuality score", punctualityScore);
        requireScore("care quality score", careQualityScore);

        String notes = feedbackNotes == null || feedbackNotes.isBlank()
                ? null
                : feedbackNotes.strip();

        return new CaregiverReview(
                null,
                familyMemberId,
                elderId,
                caregiverId,
                periodStart,
                periodEnd,
                overallRating,
                punctualityScore,
                careQualityScore,
                notes,
                renewalDecision,
                null);
    }

    private static void requireScore(String label, Byte score) {
        if (score != null && (score < 1 || score > 5)) {
            throw new BusinessRuleViolation(
                    "CAREGIVER_REVIEW_SCORE_INVALID",
                    "%s must be between 1 and 5".formatted(label));
        }
    }
}
