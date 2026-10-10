package sg.nus.carelink.report.controller.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import sg.nus.carelink.report.domain.model.CaregiverReview;

public record CaregiverReviewCreateRequest(
        @NotNull Long elderId,
        @NotNull Long caregiverId,
        @NotNull @PastOrPresent(message = "Review a period that has started") LocalDate periodStart,
        @NotNull @PastOrPresent(message = "Review a period that has ended") LocalDate periodEnd,
        @NotNull @Min(1) @Max(5) Byte overallRating,
        @Min(1) @Max(5) Byte punctualityScore,
        @Min(1) @Max(5) Byte careQualityScore,
        @Size(max = 2000) String feedbackNotes,
        @NotNull CaregiverReview.RenewalDecision renewalDecision) {

    /** Reported against "periodInOrder"; the browser shows it under the period's end. */
    @AssertTrue(message = "The period must end on or after its start")
    public boolean isPeriodInOrder() {
        return periodStart == null || periodEnd == null || !periodEnd.isBefore(periodStart);
    }
}
