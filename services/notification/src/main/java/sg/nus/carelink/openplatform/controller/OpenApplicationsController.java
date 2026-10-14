package sg.nus.carelink.openplatform.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.openplatform.application.OnboardingService;
import sg.nus.carelink.openplatform.application.WebhookUrlRule;
import sg.nus.carelink.openplatform.controller.dto.OpenPlatformRequests;
import sg.nus.carelink.openplatform.controller.dto.OpenPlatformResponses;
import sg.nus.carelink.openplatform.domain.model.ApplicationDetails;

/**
 * {@code POST /api/open/v1/applications}: anyone may apply to join the Open Platform (PT01). Open, without a
 * token or CSRF.
 */
@RestController
public class OpenApplicationsController {

	private final OnboardingService onboarding;
	private final WebhookUrlRule webhookUrls;

	public OpenApplicationsController(OnboardingService onboarding, WebhookUrlRule webhookUrls) {
		this.onboarding = onboarding;
		this.webhookUrls = webhookUrls;
	}

	@PostMapping("/api/open/v1/applications")
	@ResponseStatus(HttpStatus.CREATED)
	public OpenPlatformResponses.ApplicationReceived apply(@Valid @RequestBody OpenPlatformRequests.Apply request) {
		ApplicationDetails details = new ApplicationDetails(request.organisationName(), request.contactName(),
				request.contactEmail(), request.contactPhone(), request.services(),
				webhookUrls.check("webhookUrl", request.webhookUrl()));
		return new OpenPlatformResponses.ApplicationReceived(onboarding.apply(details, request.password()).reference());
	}
}
