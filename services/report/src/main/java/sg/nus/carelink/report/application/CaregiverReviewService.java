package sg.nus.carelink.report.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.report.application.CaregiverDirectory.CaregiverPublicProfile;
import sg.nus.carelink.report.domain.model.CaregiverReview;
import sg.nus.carelink.report.domain.repository.CaregiverReviewRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/** Application service for FM09 caregiver review and renewal decisions. */
@Service
@Transactional
public class CaregiverReviewService {

    private final Accounts accounts;
    private final FamilyMembers families;
    private final FamilyAccessQuery familyAccess;
    private final CaregiverDirectory caregivers;
    private final VisitScheduleQuery visits;
    private final CaregiverReviewRepository reviews;

    public CaregiverReviewService(
            Accounts accounts,
            FamilyMembers families,
            FamilyAccessQuery familyAccess,
            CaregiverDirectory caregivers,
            VisitScheduleQuery visits,
            CaregiverReviewRepository reviews) {
        this.accounts = accounts;
        this.families = families;
        this.familyAccess = familyAccess;
        this.caregivers = caregivers;
        this.visits = visits;
        this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public List<ReviewView> list(String username, Long elderId) {
        familyAccess.requireReadableElder(username, elderId);
        return reviews.findByElderId(elderId).stream()
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CaregiverPublicProfile> caregiversForReview(String username, Long elderId) {
        familyAccess.requireReadableElder(username, elderId);
        return visits.caregiverIdsForElder(elderId).stream()
                .map(caregiverId -> caregivers.findPublicProfile(caregiverId)
                        .orElseThrow(() -> new ResourceNotFound("Caregiver", caregiverId)))
                .toList();
    }

    public ReviewView submit(
            String username,
            Long elderId,
            Long caregiverId,
            LocalDate periodStart,
            LocalDate periodEnd,
            Byte overallRating,
            Byte punctualityScore,
            Byte careQualityScore,
            String feedbackNotes,
            CaregiverReview.RenewalDecision renewalDecision) {

        familyAccess.requireWritableElder(username, elderId);
        Long familyId = currentFamilyId(username);

        if (!visits.hasAssignedVisit(Set.of(elderId), caregiverId)) {
            throw new AccessDeniedException(
                    "The caregiver must have a visit relationship with the selected elder");
        }

        CaregiverPublicProfile caregiver = caregivers.findPublicProfile(caregiverId)
                .orElseThrow(() -> new ResourceNotFound("Caregiver", caregiverId));

        if (reviews.existsForPeriod(
                familyId, elderId, caregiverId, periodStart, periodEnd)) {
            throw new BusinessRuleViolation(
                    "CAREGIVER_REVIEW_ALREADY_SUBMITTED",
                    "A review for this caregiver and period has already been submitted");
        }

        CaregiverReview saved = reviews.save(
                CaregiverReview.submit(
                        familyId,
                        elderId,
                        caregiverId,
                        periodStart,
                        periodEnd,
                        overallRating,
                        punctualityScore,
                        careQualityScore,
                        feedbackNotes,
                        renewalDecision));

        return new ReviewView(saved, caregiver.fullName());
    }

    private Long currentFamilyId(String username) {
        Long userId = accounts.idOf(username);
        return families.findIdByUserId(userId)
                .orElseThrow(() -> new ResourceNotFound("FamilyMember", userId));
    }

    private ReviewView view(CaregiverReview review) {
        String name = caregivers.findPublicProfile(review.caregiverId())
                .map(CaregiverPublicProfile::fullName)
                .orElse("Caregiver #" + review.caregiverId());
        return new ReviewView(review, name);
    }

    public record ReviewView(CaregiverReview review, String caregiverName) {}
}
