package sg.nus.carelink.visit.controller;

import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sg.nus.carelink.visit.application.CaregiverSystemAlertService;

@RestController
@RequestMapping("/api/caregivers/me/system-alerts")
@PreAuthorize("hasRole('CAREGIVER')")
public class CaregiverSystemAlertController {
    private final CaregiverSystemAlertService service;
    public CaregiverSystemAlertController(CaregiverSystemAlertService service) { this.service=service; }
    @GetMapping public CaregiverSystemAlertService.Page own(Principal principal,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.own(principal.getName(),page,size);
    }
    @GetMapping("/{incidentId}") public CaregiverSystemAlertService.Summary detail(Principal principal,@PathVariable Long incidentId) {
        return service.detail(principal.getName(),incidentId);
    }
}
