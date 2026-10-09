package sg.nus.carelink.visit.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sg.nus.carelink.visit.application.CaregiverHealthService;
import sg.nus.carelink.visit.controller.dto.CaregiverHealthRequest;

@RestController
@RequestMapping("/api/visits/{visitId}/health-records")
@PreAuthorize("hasRole('CAREGIVER')")
public class CaregiverHealthController {
    private final CaregiverHealthService service;
    public CaregiverHealthController(CaregiverHealthService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<CaregiverHealthService.Saved> record(Authentication auth, @PathVariable Long visitId,
            @Valid @RequestBody CaregiverHealthRequest input) {
        var saved = service.record(auth.getName(), visitId, input.expectedVersion(), input.clientRequestId(), input.measurement());
        return ResponseEntity.status(saved.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(saved);
    }
    @GetMapping
    public CaregiverHealthService.Page history(Authentication auth, @PathVariable Long visitId,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 50) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid health history pagination.");
        }
        return service.history(auth.getName(), visitId, page, size);
    }
}
