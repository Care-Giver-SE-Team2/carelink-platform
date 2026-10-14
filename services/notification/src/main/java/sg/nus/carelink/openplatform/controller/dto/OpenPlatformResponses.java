package sg.nus.carelink.openplatform.controller.dto;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import sg.nus.carelink.openplatform.application.PortalService;
import sg.nus.carelink.openplatform.application.ProviderReviewService;
import sg.nus.carelink.openplatform.domain.model.ApplicationDetails;
import sg.nus.carelink.openplatform.domain.model.Delivery;
import sg.nus.carelink.openplatform.domain.model.DeliveryAttempt;
import sg.nus.carelink.openplatform.domain.model.InvalidField;
import sg.nus.carelink.openplatform.domain.model.Provider;
import sg.nus.carelink.openplatform.domain.model.ProviderApplication;
import sg.nus.carelink.openplatform.domain.model.WebhookEvent;

/** What the Open Platform answers, in the contract's shapes (docs/api/open-platform.yaml, components). */
public final class OpenPlatformResponses {

	private OpenPlatformResponses() {
	}

	public record ApplicationReceived(String applicationId) {
	}

	/** {@code Application}. */
	public record Application(String id, String organisationName, String contactName, String contactEmail,
			String contactPhone, List<String> services, URI webhookUrl, String status, OffsetDateTime submittedAt,
			OffsetDateTime reviewedAt, String reason, boolean suspended) {

		public static Application of(PortalService.MyApplication mine) {
			ProviderApplication application = mine.application();
			ApplicationDetails details = application.details();
			return new Application(application.reference(), details.organisationName(), details.contactName(),
					details.contactEmail(), details.contactPhone(), details.services(), details.webhookUrl(),
					application.status().name(), OpenTimes.of(application.submittedAt()),
					OpenTimes.of(application.reviewedAt()), application.reason(), mine.suspended());
		}
	}

	public record IssuedCredentials(String clientId, String clientSecret, String webhookSecret) {

		public static IssuedCredentials of(PortalService.IssuedCredentials issued) {
			return new IssuedCredentials(issued.clientId(), issued.clientSecret(), issued.webhookSecret());
		}
	}

	public record RotatedClientSecret(String clientId, String clientSecret) {
	}

	public record RotatedWebhookSecret(String webhookSecret) {
	}

	/** {@code CredentialsSummary}. */
	public record CredentialsSummary(String clientId, String secretHint, OffsetDateTime secretIssuedAt,
			List<String> scopes, String tokenUrl) {

		public static CredentialsSummary of(PortalService.CredentialsSummary summary) {
			return new CredentialsSummary(summary.clientId(), summary.secretHint(), OpenTimes.of(summary.secretIssuedAt()),
					summary.scopes().stream().sorted().toList(), summary.tokenUrl());
		}
	}

	/** {@code IntegrationHealth}. */
	public record IntegrationHealth(long delivered, long retrying, long failed, OffsetDateTime lastDeliveredAt,
			boolean suspended) {

		public static IntegrationHealth of(PortalService.IntegrationHealth health) {
			return new IntegrationHealth(health.delivered(), health.retrying(), health.failed(),
					OpenTimes.of(health.lastDeliveredAt()), health.suspended());
		}
	}

	/** {@code WebhookSettings}. */
	public record WebhookSettings(URI url, List<String> events, String secretHint, OffsetDateTime secretIssuedAt) {

		public static WebhookSettings of(PortalService.WebhookSettings settings) {
			return new WebhookSettings(settings.url(),
					settings.events().stream().map(WebhookEvent::wireName).sorted().toList(), settings.secretHint(),
					OpenTimes.of(settings.secretIssuedAt()));
		}
	}

	/** {@code DeliveryAttempt}. */
	public record Attempt(OffsetDateTime at, Integer responseStatus, long durationMs) {

		static Attempt of(DeliveryAttempt attempt) {
			return new Attempt(OpenTimes.of(attempt.at()), attempt.responseStatus(), attempt.durationMs());
		}
	}

	public record Request(URI url, Map<String, String> headers, String body) {
	}

	/** {@code Delivery}: the contract calls a PENDING delivery RETRYING. */
	public record DeliveryView(String id, String event, String jobId, OffsetDateTime createdAt, String status,
			List<Attempt> attempts, OffsetDateTime nextAttemptAt, Request request) {

		public static DeliveryView of(PortalService.DeliveryView view) {
			Delivery delivery = view.delivery();
			return new DeliveryView(delivery.id(), delivery.event().wireName(), delivery.jobId(),
					OpenTimes.of(delivery.createdAt()), statusName(delivery.status()),
					view.attempts().stream().map(Attempt::of).toList(), OpenTimes.of(delivery.nextAttemptAt()),
					new Request(delivery.url(), view.headers(), delivery.body()));
		}
	}

	public record DeliveryPage(List<DeliveryView> items, int page, int size, long totalElements) {

		public static DeliveryPage of(PortalService.DeliveryPage page) {
			return new DeliveryPage(page.items().stream().map(DeliveryView::of).toList(), page.page(), page.size(),
					page.totalElements());
		}
	}

	/** The contract's name for a delivery's status. */
	public static String statusName(Delivery.Status status) {
		return status == Delivery.Status.PENDING ? "RETRYING" : status.name();
	}

	/** The delivery status a contract name stands for. */
	public static Delivery.Status status(String name) {
		return switch (name) {
			case "DELIVERED" -> Delivery.Status.DELIVERED;
			case "RETRYING" -> Delivery.Status.PENDING;
			case "FAILED" -> Delivery.Status.FAILED;
			default -> throw new InvalidField("status", "Must be DELIVERED, RETRYING or FAILED.");
		};
	}

	// ---------- The manager's views (internal, /api) ----------

	/** An application waiting for a decision, as the manager's screen lists it. */
	public record ApplicationForReview(long id, String reference, String organisationName, String contactName,
			String contactEmail, String contactPhone, List<String> services, URI webhookUrl,
			OffsetDateTime submittedAt) {

		public static ApplicationForReview of(ProviderApplication application) {
			ApplicationDetails details = application.details();
			return new ApplicationForReview(application.id(), application.reference(), details.organisationName(),
					details.contactName(), details.contactEmail(), details.contactPhone(), details.services(),
					details.webhookUrl(), OpenTimes.of(application.submittedAt()));
		}
	}

	/** An approved provider with its integration's health. The manager never sees a secret. */
	public record ProviderView(long id, String reference, String organisationName, String contactName,
			String contactEmail, String contactPhone, List<String> services, URI webhookUrl, String clientId,
			String status, OffsetDateTime submittedAt, OffsetDateTime approvedAt, boolean credentialsCollected,
			IntegrationHealth health) {

		public static ProviderView of(ProviderReviewService.ProviderDetails details, PortalService.IntegrationHealth health) {
			Provider provider = details.provider();
			ProviderApplication application = details.application();
			return new ProviderView(provider.id(), application.reference(), provider.organisationName(),
					application.details().contactName(), provider.contactEmail(), application.details().contactPhone(),
					provider.services(), details.webhookUrl(), provider.clientId(), provider.status().name(),
					OpenTimes.of(application.submittedAt()), OpenTimes.of(provider.approvedAt()), provider.collected(),
					IntegrationHealth.of(health));
		}
	}
}
