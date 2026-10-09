package sg.nus.carelink.report.controller.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import sg.nus.carelink.report.domain.model.CaregiverReview;

public record CaregiverReviewCreateRequest(
        @NotNull Long elderId,
        @NotNull Long caregiverId,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotNull @Min(1) @Max(5) Byte overallRating,
        @Min(1) @Max(5) Byte punctualityScore,
        @Min(1) @Max(5) Byte careQualityScore,
        String feedbackNotes,
        @NotNull CaregiverReview.RenewalDecision renewalDecision) {
}
