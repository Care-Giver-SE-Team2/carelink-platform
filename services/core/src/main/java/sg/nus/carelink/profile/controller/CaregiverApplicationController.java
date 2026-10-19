package sg.nus.carelink.profile.controller;

import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sg.nus.carelink.profile.application.CaregiverAdmissionService;
import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.profile.controller.dto.CaregiverAdmissionRequests.*;
import sg.nus.carelink.profile.domain.model.CaregiverApplication.View;

@RestController
@RequestMapping("/api/caregivers/me")
@PreAuthorize("hasRole('CAREGIVER')")
public class CaregiverApplicationController {
    private final CaregiverAdmissionService service;
    private final CaregiverWorkDirectory directory;
    public CaregiverApplicationController(CaregiverAdmissionService service,CaregiverWorkDirectory directory) { this.service=service; this.directory=directory; }
    @GetMapping("/application") public View own(Principal user) { return service.own(user.getName()); }
    @GetMapping("/profile") public CaregiverWorkDirectory.Profile profile(Principal user) { return directory.require(user.getName()); }
    @PutMapping("/application") public View edit(Principal user,@Valid @RequestBody Edit request) {
        return service.edit(user.getName(),request.details(),request.expectedVersion(),request.clientRequestId());
    }
    @PostMapping("/application/resubmissions") public View resubmit(Principal user,@Valid @RequestBody Command request) {
        return service.resubmit(user.getName(),request.expectedVersion(),request.clientRequestId());
    }
    @PutMapping("/contact") public View contact(Principal user,@Valid @RequestBody Contact request) {
        return service.contact(user.getName(),request.phone(),request.expectedVersion(),request.clientRequestId());
    }
}
