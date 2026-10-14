package sg.nus.carelink.openplatform.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.openplatform.application.PortalService;
import sg.nus.carelink.openplatform.application.ProviderReviewService;
import sg.nus.carelink.openplatform.controller.dto.OpenPlatformRequests;
import sg.nus.carelink.openplatform.controller.dto.OpenPlatformResponses;
import sg.nus.carelink.openplatform.domain.model.Provider;
import sg.nus.carelink.platform.security.SignedInUsers;

/**
 * The manager's side of the Open Platform, in the CareLink app: deciding applications and suspending
 * providers (PT01). Internal: core's session and the MANAGER role, with the CSRF cookie like every
 * {@code /api} write. The public API has none of it.
 */
@RestController
@PreAuthorize("hasRole('MANAGER')")
public class ProviderAdminController {

	private final ProviderReviewService review;
	private final PortalService portal;
	private final SignedInUsers users;

	public ProviderAdminController(ProviderReviewService review, PortalService portal, SignedInUsers users) {
		this.review = review;
		this.portal = portal;
		this.users = users;
	}

	/** GET /api/provider-applications : those waiting for a decision, newest first. */
	@GetMapping("/api/provider-applications")
	public List<OpenPlatformResponses.ApplicationForReview> pending() {
		return review.pending().stream().map(OpenPlatformResponses.ApplicationForReview::of).toList();
	}

	/** POST /api/provider-applications/{id}/approve : creates the provider, its client and its webhook endpoint. */
	@PostMapping("/api/provider-applications/{id}/approve")
	public OpenPlatformResponses.ProviderView approve(@PathVariable long id) {
		return view(review.approve(id, users.require().id()));
	}

	/** POST /api/provider-applications/{id}/reject {reason} : the provider is shown the reason. */
	@PostMapping("/api/provider-applications/{id}/reject")
	public OpenPlatformResponses.ApplicationForReview reject(@PathVariable long id,
			@Valid @RequestBody OpenPlatformRequests.Reject request) {
		return OpenPlatformResponses.ApplicationForReview.of(review.reject(id, request.reason(), users.require().id()));
	}

	/** GET /api/providers : approved providers, the most recently approved first, with their webhooks' health. */
	@GetMapping("/api/providers")
	public List<OpenPlatformResponses.ProviderView> providers() {
		return review.providers().stream().map(this::view).toList();
	}

	/** POST /api/providers/{id}/suspend : no jobs, tokens refused, webhooks stopped. */
	@PostMapping("/api/providers/{id}/suspend")
	public OpenPlatformResponses.ProviderView suspend(@PathVariable long id) {
		return view(review.suspend(id));
	}

	@PostMapping("/api/providers/{id}/reinstate")
	public OpenPlatformResponses.ProviderView reinstate(@PathVariable long id) {
		return view(review.reinstate(id));
	}

	private OpenPlatformResponses.ProviderView view(Provider provider) {
		return OpenPlatformResponses.ProviderView.of(review.details(provider), portal.healthOf(provider));
	}
}
