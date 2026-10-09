package sg.nus.carelink.report.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import sg.nus.carelink.report.application.CaregiverReviewService;
import sg.nus.carelink.report.controller.dto.CaregiverReviewCaregiverResponse;
import sg.nus.carelink.report.controller.dto.CaregiverReviewCreateRequest;
import sg.nus.carelink.report.controller.dto.CaregiverReviewResponse;

/** Family HTTP endpoints for FM09. */
@RestController
@RequestMapping("/api/family/caregiver-reviews")
@PreAuthorize("hasRole('FAMILY')")
public class CaregiverReviewController {

    private final CaregiverReviewService service;

    public CaregiverReviewController(CaregiverReviewService service) {
        this.service = service;
    }

    @GetMapping
    public List<CaregiverReviewResponse> list(
            @RequestParam Long elderId,
            Principal principal) {
        return service.list(principal.getName(), elderId).stream()
                .map(CaregiverReviewResponse::from)
                .toList();
    }

    @GetMapping("/caregivers")
    public List<CaregiverReviewCaregiverResponse> caregivers(
            @RequestParam Long elderId,
            Principal principal) {
        return service.caregiversForReview(principal.getName(), elderId).stream()
                .map(CaregiverReviewCaregiverResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CaregiverReviewResponse create(
            @Valid @RequestBody CaregiverReviewCreateRequest request,
            Principal principal) {
        return CaregiverReviewResponse.from(
                service.submit(
                        principal.getName(),
                        request.elderId(),
                        request.caregiverId(),
                        request.periodStart(),
                        request.periodEnd(),
                        request.overallRating(),
                        request.punctualityScore(),
                        request.careQualityScore(),
                        request.feedbackNotes(),
                        request.renewalDecision()));
    }
}
