package sg.nus.carelink.visit.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import sg.nus.carelink.visit.application.CaregiverIncidentReportingService;
import sg.nus.carelink.visit.controller.dto.CaregiverIncidentRequest;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('CAREGIVER')")
public class CaregiverIncidentController {
    private final CaregiverIncidentReportingService service;
    public CaregiverIncidentController(CaregiverIncidentReportingService service) { this.service = service; }
    @PostMapping("/incidents")
    public ResponseEntity<CaregiverIncidentReportingService.Result> report(Authentication auth, @Valid @RequestBody CaregiverIncidentRequest input) {
        var result = service.report(auth.getName(), input.visitId(), input.category(), input.severity(), input.description(), input.expectedVersion(), input.clientRequestId());
        return ResponseEntity.status(result.replayed() ? 200 : 201).body(result);
    }
    @GetMapping("/caregivers/me/incidents")
    public CaregiverIncidentReportingService.Page list(Authentication auth, @RequestParam(required=false) Long visitId,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        if (page < 0 || size < 1 || size > 50 || (visitId != null && visitId <= 0)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report page");
        return service.list(auth.getName(), visitId, page, size);
    }
    @GetMapping("/caregivers/me/incidents/{id}")
    public CaregiverIncidentReportingService.View own(Authentication auth, @PathVariable Long id) { return service.own(auth.getName(), id); }
}
