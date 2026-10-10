package sg.nus.carelink.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import sg.nus.carelink.report.application.CaregiverDirectory.CaregiverPublicProfile;
import sg.nus.carelink.report.domain.model.CaregiverReview;
import sg.nus.carelink.report.domain.repository.CaregiverReviewRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

class CaregiverReviewServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);

    private Accounts accounts;
    private FamilyMembers families;
    private FamilyAccessQuery familyAccess;
    private CaregiverDirectory caregivers;
    private VisitScheduleQuery visits;
    private CaregiverReviewRepository reviews;
    private CaregiverReviewService service;

    @BeforeEach
    void setUp() {
        accounts = mock(Accounts.class);
        families = mock(FamilyMembers.class);
        familyAccess = mock(FamilyAccessQuery.class);
        caregivers = mock(CaregiverDirectory.class);
        visits = mock(VisitScheduleQuery.class);
        reviews = mock(CaregiverReviewRepository.class);
        service = new CaregiverReviewService(accounts, families, familyAccess, caregivers, visits, reviews);
    }

    @Test
    void listsReviewsOnlyAfterReadableAccessCheckAndAddsCaregiverName() {
        CaregiverReview review = storedReview();
        when(reviews.findByElderId(21L)).thenReturn(List.of(review));
        when(caregivers.findPublicProfile(31L)).thenReturn(Optional.of(caregiver()));

        List<CaregiverReviewService.ReviewView> result = service.list("family_test", 21L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().review()).isEqualTo(review);
        assertThat(result.getFirst().caregiverName()).isEqualTo("John Tan");
        InOrder order = inOrder(familyAccess, reviews);
        order.verify(familyAccess).requireReadableElder("family_test", 21L);
        order.verify(reviews).findByElderId(21L);
    }

    @Test
    void reviewHistoryStillWorksWhenCaregiverProfileIsMissing() {
        when(reviews.findByElderId(21L)).thenReturn(List.of(storedReview()));
        when(caregivers.findPublicProfile(31L)).thenReturn(Optional.empty());

        assertThat(service.list("family_test", 21L).getFirst().caregiverName())
                .isEqualTo("Caregiver #31");
    }

    @Test
    void listsOnlyCaregiversWithVisitRelationships() {
        when(visits.caregiverIdsForElder(21L)).thenReturn(List.of(31L, 32L));
        when(caregivers.findPublicProfile(31L)).thenReturn(Optional.of(caregiver()));
        when(caregivers.findPublicProfile(32L)).thenReturn(Optional.of(new CaregiverPublicProfile(32L, "Mary Lim", List.of())));

        assertThat(service.caregiversForReview("family_test", 21L))
                .extracting(CaregiverPublicProfile::id)
                .containsExactly(31L, 32L);
        verify(familyAccess).requireReadableElder("family_test", 21L);
    }

    @Test
    void missingCaregiverProfileInReviewableListIsReported() {
        when(visits.caregiverIdsForElder(21L)).thenReturn(List.of(999L));
        when(caregivers.findPublicProfile(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.caregiversForReview("family_test", 21L))
                .isInstanceOf(ResourceNotFound.class);
    }

    @Test
    void submitsReviewAfterWritableAccessAndVisitRelationshipChecks() {
        prepareFamily();
        when(visits.hasAssignedVisit(Set.of(21L), 31L)).thenReturn(true);
        when(caregivers.findPublicProfile(31L)).thenReturn(Optional.of(caregiver()));
        when(reviews.existsForPeriod(11L, 21L, 31L, START, END)).thenReturn(false);
        when(reviews.save(any(CaregiverReview.class))).thenAnswer(invocation -> {
            CaregiverReview value = invocation.getArgument(0);
            return new CaregiverReview(51L, value.familyMemberId(), value.elderId(), value.caregiverId(),
                    value.periodStart(), value.periodEnd(), value.overallRating(), value.punctualityScore(),
                    value.careQualityScore(), value.feedbackNotes(), value.renewalDecision(),
                    LocalDateTime.of(2026, 10, 7, 20, 0));
        });

        CaregiverReviewService.ReviewView result = service.submit(
                "family_test", 21L, 31L, START, END,
                (byte) 5, (byte) 4, (byte) 5,
                "  Very patient.  ", CaregiverReview.RenewalDecision.RENEW_CURRENT);

        verify(familyAccess).requireWritableElder("family_test", 21L);
        verify(visits).hasAssignedVisit(Set.of(21L), 31L);
        assertThat(result.review().id()).isEqualTo(51L);
        assertThat(result.review().familyMemberId()).isEqualTo(11L);
        assertThat(result.review().feedbackNotes()).isEqualTo("Very patient.");
        assertThat(result.caregiverName()).isEqualTo("John Tan");
    }

    @Test
    void rejectsCaregiverWithoutVisitRelationship() {
        prepareFamily();
        when(visits.hasAssignedVisit(Set.of(21L), 31L)).thenReturn(false);

        assertThatThrownBy(() -> submit())
                .isInstanceOf(AccessDeniedException.class);
        verify(reviews, never()).save(any());
    }

    @Test
    void rejectsDuplicateReviewForSameFamilyElderCaregiverAndPeriod() {
        prepareFamily();
        when(visits.hasAssignedVisit(Set.of(21L), 31L)).thenReturn(true);
        when(caregivers.findPublicProfile(31L)).thenReturn(Optional.of(caregiver()));
        when(reviews.existsForPeriod(11L, 21L, 31L, START, END)).thenReturn(true);

        assertThatThrownBy(() -> submit())
                .isInstanceOf(BusinessRuleViolation.class)
                .extracting(error -> ((BusinessRuleViolation) error).code())
                .isEqualTo("CAREGIVER_REVIEW_ALREADY_SUBMITTED");
        verify(reviews, never()).save(any());
    }

    @Test
    void missingFamilyProfileIsReported() {
        when(visits.hasAssignedVisit(Set.of(21L), 31L)).thenReturn(true);
        when(accounts.idOf("family_test")).thenReturn(3L);
        when(families.findIdByUserId(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submit()).isInstanceOf(ResourceNotFound.class);
        verify(reviews, never()).save(any());
    }

    private CaregiverReviewService.ReviewView submit() {
        return service.submit("family_test", 21L, 31L, START, END,
                (byte) 5, (byte) 4, (byte) 5, "Good care",
                CaregiverReview.RenewalDecision.RENEW_CURRENT);
    }

    private void prepareFamily() {
        when(accounts.idOf("family_test")).thenReturn(3L);
        when(families.findIdByUserId(3L)).thenReturn(Optional.of(11L));
    }

    private CaregiverPublicProfile caregiver() {
        return new CaregiverPublicProfile(31L, "John Tan", List.of("English"));
    }

    private CaregiverReview storedReview() {
        return new CaregiverReview(51L, 11L, 21L, 31L, START, END,
                (byte) 5, (byte) 4, (byte) 5, "Good care",
                CaregiverReview.RenewalDecision.RENEW_CURRENT,
                LocalDateTime.of(2026, 10, 7, 20, 0));
    }
}
