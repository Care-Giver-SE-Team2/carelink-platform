package sg.nus.carelink.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sg.nus.carelink.profile.application.CaregiverAdmissionService;
import sg.nus.carelink.profile.controller.dto.CaregiverAdmissionRequests.Registration;
import sg.nus.carelink.profile.domain.model.CaregiverApplication.View;

/** Anonymous, CSRF protected, fixed CAREGIVER role. Success does not approve admission. */
@RestController
@RequestMapping("/api/caregiver-registrations")
public class CaregiverRegistrationController {
    private final CaregiverAdmissionService service;
    public CaregiverRegistrationController(CaregiverAdmissionService service) { this.service=service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public View register(@Valid @RequestBody Registration request) {
        return service.register(request.username(),request.password(),request.details());
    }
}
