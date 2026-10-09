package sg.nus.carelink.report.controller.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import sg.nus.carelink.report.application.CaregiverReviewService;
import sg.nus.carelink.report.domain.model.CaregiverReview;

public record CaregiverReviewResponse(
        Long id,
        Long familyMemberId,
        Long elderId,
        Long caregiverId,
        String caregiverName,
        LocalDate periodStart,
        LocalDate periodEnd,
        Byte overallRating,
        Byte punctualityScore,
        Byte careQualityScore,
        String feedbackNotes,
        CaregiverReview.RenewalDecision renewalDecision,
        LocalDateTime createdAt) {

    public static CaregiverReviewResponse from(CaregiverReviewService.ReviewView view) {
        CaregiverReview review = view.review();
        return new CaregiverReviewResponse(
                review.id(), review.familyMemberId(), review.elderId(), review.caregiverId(),
                view.caregiverName(), review.periodStart(), review.periodEnd(), review.overallRating(),
                review.punctualityScore(), review.careQualityScore(), review.feedbackNotes(),
                review.renewalDecision(), review.createdAt());
    }
}
