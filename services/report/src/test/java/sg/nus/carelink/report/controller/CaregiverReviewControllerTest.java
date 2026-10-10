package sg.nus.carelink.report.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.application.CaregiverDirectory.CaregiverPublicProfile;
import sg.nus.carelink.report.application.CaregiverReviewService;
import sg.nus.carelink.report.controller.dto.CaregiverReviewCreateRequest;
import sg.nus.carelink.report.controller.dto.CaregiverReviewResponse;
import sg.nus.carelink.report.domain.model.CaregiverReview;

class CaregiverReviewControllerTest {

    private CaregiverReviewService service;
    private CaregiverReviewController controller;
    private Principal principal;

    @BeforeEach
    void setUp() {
        service = mock(CaregiverReviewService.class);
        controller = new CaregiverReviewController(service);
        principal = () -> "family_test";
    }

    @Test
    void listsReviewsForSelectedElder() {
        when(service.list("family_test", 21L)).thenReturn(List.of(view()));

        List<CaregiverReviewResponse> result = controller.list(21L, principal);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().caregiverName()).isEqualTo("John Tan");
        assertThat(result.getFirst().renewalDecision()).isEqualTo(CaregiverReview.RenewalDecision.RENEW_CURRENT);
        verify(service).list("family_test", 21L);
    }

    @Test
    void listsReviewableCaregiversForSelectedElder() {
        when(service.caregiversForReview("family_test", 21L))
                .thenReturn(List.of(new CaregiverPublicProfile(31L, "John Tan", List.of())));

        var result = controller.caregivers(21L, principal);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(31L);
        assertThat(result.getFirst().fullName()).isEqualTo("John Tan");
    }

    @Test
    void createsReviewUsingAuthenticatedFamily() {
        CaregiverReviewCreateRequest request = new CaregiverReviewCreateRequest(
                21L, 31L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                (byte) 5, (byte) 4, (byte) 5,
                "Very patient.", CaregiverReview.RenewalDecision.REQUEST_CHANGE);
        CaregiverReview review = new CaregiverReview(
                52L, 11L, 21L, 31L, request.periodStart(), request.periodEnd(),
                request.overallRating(), request.punctualityScore(), request.careQualityScore(),
                request.feedbackNotes(), request.renewalDecision(), LocalDateTime.of(2026, 10, 7, 20, 0));
        when(service.submit("family_test", 21L, 31L, request.periodStart(), request.periodEnd(),
                (byte) 5, (byte) 4, (byte) 5, "Very patient.",
                CaregiverReview.RenewalDecision.REQUEST_CHANGE))
                .thenReturn(new CaregiverReviewService.ReviewView(review, "John Tan"));

        CaregiverReviewResponse result = controller.create(request, principal);

        assertThat(result.id()).isEqualTo(52L);
        assertThat(result.caregiverName()).isEqualTo("John Tan");
        assertThat(result.renewalDecision()).isEqualTo(CaregiverReview.RenewalDecision.REQUEST_CHANGE);
    }

    private CaregiverReviewService.ReviewView view() {
        CaregiverReview review = new CaregiverReview(
                51L, 11L, 21L, 31L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                (byte) 5, (byte) 4, (byte) 5, "Good care",
                CaregiverReview.RenewalDecision.RENEW_CURRENT,
                LocalDateTime.of(2026, 10, 7, 20, 0));
        return new CaregiverReviewService.ReviewView(review, "John Tan");
    }
}
