package sg.nus.carelink.profile.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.profile.application.FamilyRegistrationService;
import sg.nus.carelink.profile.controller.dto.FamilyRegistrationRequest;
import sg.nus.carelink.profile.controller.dto.FamilyRegistrationResponse;

/**
 * Family sign-up from the landing page. Deliberately has no @PreAuthorize: the caller has no
 * account yet, so SecurityConfig and WebMvcConfig both open this one path to anonymous callers.
 * It does not sign the caller in; the client then calls /api/auth/login with the same details.
 */
@RestController
@RequestMapping("/api/family-registrations")
public class FamilyRegistrationController {

	private final FamilyRegistrationService registrations;

	public FamilyRegistrationController(FamilyRegistrationService registrations) {
		this.registrations = registrations;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public FamilyRegistrationResponse register(@Valid @RequestBody FamilyRegistrationRequest request) {
		return FamilyRegistrationResponse.from(
				registrations.register(request.username(), request.password(), request.fullName(), request.phone()),
				request.username());
	}
}
