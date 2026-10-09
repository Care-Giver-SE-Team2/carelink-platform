package sg.nus.carelink.report.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.profile.application.CaregiverDirectory;
import sg.nus.carelink.profile.application.CaregiverPublicProfile;
import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;
import sg.nus.carelink.report.domain.model.CaregiverReview;
import sg.nus.carelink.report.domain.repository.CaregiverReviewRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.repository.VisitScheduleQuery;

/** Application service for FM09 caregiver review and renewal decisions. */
@Service
@Transactional
public class CaregiverReviewService {

    private final IdentityService identity;
    private final FamilyMemberRepository families;
    private final FamilyAccessQuery familyAccess;
    private final CaregiverDirectory caregivers;
    private final VisitScheduleQuery visits;
    private final CaregiverReviewRepository reviews;

    public CaregiverReviewService(
            IdentityService identity,
            FamilyMemberRepository families,
            FamilyAccessQuery familyAccess,
            CaregiverDirectory caregivers,
            VisitScheduleQuery visits,
            CaregiverReviewRepository reviews) {
        this.identity = identity;
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
        FamilyMember family = currentFamily(username);

        if (!visits.hasAssignedVisit(Set.of(elderId), caregiverId)) {
            throw new AccessDeniedException(
                    "The caregiver must have a visit relationship with the selected elder");
        }

        CaregiverPublicProfile caregiver = caregivers.findPublicProfile(caregiverId)
                .orElseThrow(() -> new ResourceNotFound("Caregiver", caregiverId));

        if (reviews.existsForPeriod(
                family.id(), elderId, caregiverId, periodStart, periodEnd)) {
            throw new BusinessRuleViolation(
                    "CAREGIVER_REVIEW_ALREADY_SUBMITTED",
                    "A review for this caregiver and period has already been submitted");
        }

        CaregiverReview saved = reviews.save(
                CaregiverReview.submit(
                        family.id(),
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

    private FamilyMember currentFamily(String username) {
        var account = identity.require(username);
        return families.findByUserId(account.id())
                .orElseThrow(() -> new ResourceNotFound("FamilyMember", account.id()));
    }

    private ReviewView view(CaregiverReview review) {
        String name = caregivers.findPublicProfile(review.caregiverId())
                .map(CaregiverPublicProfile::fullName)
                .orElse("Caregiver #" + review.caregiverId());
        return new ReviewView(review, name);
    }

    public record ReviewView(CaregiverReview review, String caregiverName) {}
}
