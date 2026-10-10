package sg.nus.carelink.visit.controller;

import java.security.Principal;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.visit.application.CaregiverWorkService;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('CAREGIVER')")
public class CaregiverWorkController {
    private final CaregiverWorkService service;
    public CaregiverWorkController(CaregiverWorkService service) { this.service = service; }

    @GetMapping("/caregivers/me")
    public CaregiverWorkDirectory.Profile profile(Principal principal) { return service.profile(principal.getName()); }

    @GetMapping("/caregivers/me/schedule")
    public CaregiverWorkService.Schedule schedule(Principal principal,
            @RequestParam(required = false) LocalDate dateFrom, @RequestParam(required = false) LocalDate dateTo) {
        return service.schedule(principal.getName(), dateFrom, dateTo);
    }

    @GetMapping("/visits/{visitId}/work-pack")
    public CaregiverWorkService.WorkPack workPack(Principal principal, @PathVariable Long visitId) {
        return service.workPack(principal.getName(), visitId);
    }

    @ExceptionHandler(CaregiverWorkService.InvalidDateRange.class)
    ProblemDetail invalidDates(CaregiverWorkService.InvalidDateRange error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }
}
