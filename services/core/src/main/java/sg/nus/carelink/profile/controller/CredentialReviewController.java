package sg.nus.carelink.profile.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.profile.application.CredentialRegisterRow;
import sg.nus.carelink.profile.application.CredentialReviewService;
import sg.nus.carelink.profile.controller.dto.RejectCredentialRequest;
import sg.nus.carelink.shared.error.ResourceNotFound;

/** The manager's certification register and the review of submitted certificates (UC-MG06). */
@RestController
@RequestMapping("/api/credentials")
@PreAuthorize("hasRole('MANAGER')")
public class CredentialReviewController {

	private final CredentialReviewService service;
	private final UserDirectory users;

	public CredentialReviewController(CredentialReviewService service, UserDirectory users) {
		this.service = service;
		this.users = users;
	}

	@GetMapping
	public List<CredentialRegisterRow> register() {
		return service.register();
	}

	@PostMapping("/{id}/publish")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void publish(@PathVariable Long id, Authentication authentication) {
		service.publish(id, actingUserId(authentication));
	}

	@PostMapping("/{id}/reject")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reject(@PathVariable Long id, @Valid @RequestBody RejectCredentialRequest request,
			Authentication authentication) {
		service.reject(id, actingUserId(authentication), request.reason());
	}

	private Long actingUserId(Authentication authentication) {
		return users.findByUsername(authentication.getName())
				.orElseThrow(() -> new ResourceNotFound("Account", authentication.getName()))
				.id();
	}
}
