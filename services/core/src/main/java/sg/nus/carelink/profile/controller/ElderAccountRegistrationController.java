package sg.nus.carelink.profile.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sg.nus.carelink.profile.application.ElderAccountRegistrationService;
import sg.nus.carelink.profile.controller.dto.ElderAccountRegistrationRequest;
import sg.nus.carelink.profile.controller.dto.ElderAccountRegistrationResponse;

@RestController
@RequestMapping("/api/elder-registrations")
public class ElderAccountRegistrationController {
    private final ElderAccountRegistrationService registrations;

    public ElderAccountRegistrationController(ElderAccountRegistrationService registrations) {
        this.registrations = registrations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ElderAccountRegistrationResponse register(@Valid @RequestBody ElderAccountRegistrationRequest request) {
        return new ElderAccountRegistrationResponse(
                registrations.register(request.fullName(), request.username(), request.password()), request.username());
    }
}
