package sg.nus.carelink.openplatform.controller;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.nus.carelink.openplatform.application.PortalService;
import sg.nus.carelink.openplatform.application.ProviderIdentities;
import sg.nus.carelink.openplatform.controller.dto.OpenPlatformRequests;
import sg.nus.carelink.openplatform.controller.dto.OpenPlatformResponses;
import sg.nus.carelink.openplatform.domain.model.WebhookEvent;
import sg.nus.carelink.platform.security.OpenPlatformCallers;

/**
 * The Partner Portal's API ({@code /api/open/v1/portal/**}, docs/api/open-platform.yaml, Portal and Webhooks):
 * a signed-in person at a provider, named by their portal user's token, never by the request. The token
 * was checked before this runs ({@code OpenPlatformSecurityConfiguration}).
 */
@RestController
@Validated
@RequestMapping("/api/open/v1/portal")
public class PortalController {

	private final PortalService portal;
	private final OpenPlatformCallers callers;

	public PortalController(PortalService portal, OpenPlatformCallers callers) {
		this.portal = portal;
		this.callers = callers;
	}

	@GetMapping("/application")
	public OpenPlatformResponses.Application application() {
		return OpenPlatformResponses.Application.of(portal.application(me()));
	}

	@PostMapping("/credentials/collect")
	public OpenPlatformResponses.IssuedCredentials collectCredentials() {
		return OpenPlatformResponses.IssuedCredentials.of(portal.collectCredentials(me()));
	}

	@GetMapping("/credentials")
	public OpenPlatformResponses.CredentialsSummary credentials() {
		return OpenPlatformResponses.CredentialsSummary.of(portal.credentials(me()));
	}

	@PostMapping("/credentials/rotate")
	public OpenPlatformResponses.RotatedClientSecret rotateClientSecret() {
		ProviderIdentities.ClientCredentials rotated = portal.rotateClientSecret(me());
		return new OpenPlatformResponses.RotatedClientSecret(rotated.clientId(), rotated.clientSecret());
	}

	@GetMapping("/health")
	public OpenPlatformResponses.IntegrationHealth health() {
		return OpenPlatformResponses.IntegrationHealth.of(portal.health(me()));
	}

	@GetMapping("/webhook")
	public OpenPlatformResponses.WebhookSettings webhookSettings() {
		return OpenPlatformResponses.WebhookSettings.of(portal.webhookSettings(me()));
	}

	@PutMapping("/webhook")
	public OpenPlatformResponses.WebhookSettings updateWebhookSettings(
			@Valid @RequestBody OpenPlatformRequests.WebhookSettingsUpdate request) {
		Set<WebhookEvent> events = request.events().stream().map(WebhookEvent::fromWireName).collect(Collectors.toSet());
		return OpenPlatformResponses.WebhookSettings.of(portal.updateWebhookSettings(me(), request.url(), events));
	}

	@PostMapping("/webhook/secret/rotate")
	public OpenPlatformResponses.RotatedWebhookSecret rotateWebhookSecret() {
		return new OpenPlatformResponses.RotatedWebhookSecret(portal.rotateWebhookSecret(me()));
	}

	@PostMapping("/webhook/test")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public OpenPlatformResponses.DeliveryView sendTestEvent() {
		return OpenPlatformResponses.DeliveryView.of(portal.sendTestEvent(me()));
	}

	@GetMapping("/webhook/deliveries")
	public OpenPlatformResponses.DeliveryPage deliveries(
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return OpenPlatformResponses.DeliveryPage.of(portal.deliveries(me(),
				status == null ? null : OpenPlatformResponses.status(status), page, size));
	}

	@GetMapping("/webhook/deliveries/{deliveryId}")
	public OpenPlatformResponses.DeliveryView delivery(@PathVariable String deliveryId) {
		return OpenPlatformResponses.DeliveryView.of(portal.delivery(me(), deliveryId));
	}

	@PostMapping("/webhook/deliveries/{deliveryId}/resend")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public OpenPlatformResponses.DeliveryView resend(@PathVariable String deliveryId) {
		return OpenPlatformResponses.DeliveryView.of(portal.resend(me(), deliveryId));
	}

	/** The portal user's {@code sub}, from the token. */
	private String me() {
		return callers.requirePortalUser().subject();
	}
}
