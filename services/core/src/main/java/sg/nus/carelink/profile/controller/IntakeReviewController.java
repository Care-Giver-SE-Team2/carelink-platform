package sg.nus.carelink.profile.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.profile.application.IntakeReviewService;
import sg.nus.carelink.profile.controller.dto.IntakeDecisionRequest;
import sg.nus.carelink.profile.controller.dto.IntakeDecisionResponse;
import sg.nus.carelink.profile.controller.dto.IntakeReviewResponse;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * The manager's review of family intake applications: the ones waiting for an answer, and
 * approving (which creates the elder record and the elder's login) or declining one. The family reads the outcome
 * through /api/intake-applications.
 */
@RestController
@RequestMapping("/api/intake-reviews")
@PreAuthorize("hasRole('MANAGER')")
public class IntakeReviewController {

	private final IntakeReviewService service;
	private final UserDirectory users;

	public IntakeReviewController(IntakeReviewService service, UserDirectory users) {
		this.service = service;
		this.users = users;
	}

	@GetMapping
	public List<IntakeReviewResponse> pending() {
		return service.pending().stream().map(IntakeReviewResponse::from).toList();
	}

	/** Never cached: the response carries the elder's temporary password, shown to the manager once. */
	@PostMapping("/{id}/approve")
	public ResponseEntity<IntakeDecisionResponse> approve(@PathVariable Long id,
			@Valid @RequestBody(required = false) IntakeDecisionRequest request, Authentication authentication) {
		String message = request == null ? null : request.message();
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(IntakeDecisionResponse.approved(service.approve(id, actingUserId(authentication), message)));
	}

	@PostMapping("/{id}/decline")
	public IntakeDecisionResponse decline(@PathVariable Long id, @Valid @RequestBody IntakeDecisionRequest request,
			Authentication authentication) {
		return IntakeDecisionResponse.declined(service.decline(id, actingUserId(authentication), request.message()));
	}

	private Long actingUserId(Authentication authentication) {
		return users.findByUsername(authentication.getName())
				.orElseThrow(() -> new ResourceNotFound("Account", authentication.getName()))
				.id();
	}
}
