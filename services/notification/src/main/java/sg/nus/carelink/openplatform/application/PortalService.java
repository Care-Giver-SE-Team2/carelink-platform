package sg.nus.carelink.openplatform.application;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.openplatform.domain.model.CredentialsAlreadyCollected;
import sg.nus.carelink.openplatform.domain.model.Delivery;
import sg.nus.carelink.openplatform.domain.model.DeliveryAttempt;
import sg.nus.carelink.openplatform.domain.model.Provider;
import sg.nus.carelink.openplatform.domain.model.ProviderApplication;
import sg.nus.carelink.openplatform.domain.model.ProviderNotApproved;
import sg.nus.carelink.openplatform.domain.model.WebhookEndpoint;
import sg.nus.carelink.openplatform.domain.model.WebhookEvent;
import sg.nus.carelink.openplatform.domain.repository.ProviderApplications;
import sg.nus.carelink.openplatform.domain.repository.Providers;
import sg.nus.carelink.openplatform.domain.repository.WebhookDeliveries;
import sg.nus.carelink.openplatform.domain.repository.WebhookEndpoints;
import sg.nus.carelink.platform.security.OpenPlatformTokens;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * The Partner Portal's side of the Open Platform ({@code /api/open/v1/portal/**}): what a signed-in person
 * at a provider sees and changes. Everyone is named by their portal user's {@code sub}, from the token,
 * never from the request. Apart from the application itself, everything here is an approved
 * provider's: anyone else is refused with {@link ProviderNotApproved}.
 */
@Service
public class PortalService {

	/** How long the signing secret before a rotation is still signed with (docs/api/webhooks.md, section 5). */
	static final Duration SECRET_OVERLAP = Duration.ofHours(24);

	/** How far back the delivery log goes. */
	static final Duration LOG_PERIOD = Duration.ofDays(30);

	/** How far back the integration's health looks. */
	static final Duration HEALTH_PERIOD = Duration.ofDays(7);

	static final Set<String> SCOPES = Set.of(OpenPlatformTokens.JOBS_READ, OpenPlatformTokens.JOBS_WRITE);

	private final ProviderApplications applications;
	private final Providers providers;
	private final WebhookEndpoints endpoints;
	private final WebhookDeliveries deliveries;
	private final WebhookDeliveryService webhooks;
	private final ProviderIdentities identities;
	private final SigningSecrets secrets;
	private final ProviderEvents events;
	private final WebhookUrlRule webhookUrls;
	private final Clock clock;

	public PortalService(ProviderApplications applications, Providers providers, WebhookEndpoints endpoints,
			WebhookDeliveries deliveries, WebhookDeliveryService webhooks, ProviderIdentities identities,
			SigningSecrets secrets, ProviderEvents events, WebhookUrlRule webhookUrls, Clock clock) {
		this.applications = applications;
		this.providers = providers;
		this.endpoints = endpoints;
		this.deliveries = deliveries;
		this.webhooks = webhooks;
		this.identities = identities;
		this.secrets = secrets;
		this.events = events;
		this.webhookUrls = webhookUrls;
		this.clock = clock;
	}

	/** The portal user's latest application, whatever its status, and whether its provider is suspended. */
	@Transactional(readOnly = true)
	public MyApplication application(String portalUser) {
		ProviderApplication application = applications.latestOf(portalUser)
				.orElseThrow(() -> new ResourceNotFound("Application of portal user", portalUser));
		boolean suspended = application.status() == ProviderApplication.Status.APPROVED
				&& providers.findByPortalUser(portalUser).map(Provider::suspended).orElse(false);
		return new MyApplication(application, suspended);
	}

	/**
	 * Hands an approved provider its client secret and webhook signing secret, once; afterwards
	 * CareLink keeps neither.
	 *
	 * @throws BusinessRuleViolation {@code NOT_APPROVED} (409, as the contract has it for this call)
	 * @throws CredentialsAlreadyCollected when they were collected
	 */
	@Transactional
	public IssuedCredentials collectCredentials(String portalUser) {
		Provider provider = providers.findByPortalUser(portalUser)
				.orElseThrow(() -> new BusinessRuleViolation("NOT_APPROVED", "The application is not approved."));
		Provider collected = provider.collectedAt(clock.instant());
		IssuedCredentials issued = new IssuedCredentials(provider.clientId(),
				secrets.reveal(provider.uncollectedClientSecret()), secrets.reveal(provider.uncollectedWebhookSecret()));
		if (!providers.update(provider, collected)) {
			// Two tabs collecting at once: one gets them, the other is told they are gone
			throw new CredentialsAlreadyCollected();
		}
		return issued;
	}

	@Transactional(readOnly = true)
	public CredentialsSummary credentials(String portalUser) {
		Provider provider = approved(portalUser);
		return new CredentialsSummary(provider.clientId(), provider.clientSecretHint(),
				provider.clientSecretIssuedAt(), SCOPES, identities.tokenUrl());
	}

	/**
	 * A new client secret; the old one stops working at once. The client may be new too, so report is
	 * told the client id.
	 */
	@Transactional
	public ProviderIdentities.ClientCredentials rotateClientSecret(String portalUser) {
		Provider provider = approved(portalUser);
		Instant now = clock.instant();
		ProviderIdentities.ClientCredentials client = identities.replaceClient(provider.clientId(), provider.id(),
				provider.organisationName());
		Provider rotated = provider.withClient(client.clientId(), Provider.hintOf(client.clientSecret()), now);
		if (!providers.update(provider, rotated)) {
			throw concurrentUpdate();
		}
		events.changed(rotated, now);
		return client;
	}

	@Transactional(readOnly = true)
	public IntegrationHealth health(String portalUser) {
		return healthOf(approved(portalUser));
	}

	/** How the provider's webhooks did over the last seven days, for the portal and the manager's list. */
	@Transactional(readOnly = true)
	public IntegrationHealth healthOf(Provider provider) {
		Instant since = clock.instant().minus(HEALTH_PERIOD);
		return new IntegrationHealth(
				deliveries.count(provider.id(), Delivery.Status.DELIVERED, since),
				deliveries.count(provider.id(), Delivery.Status.PENDING, since),
				deliveries.count(provider.id(), Delivery.Status.FAILED, since),
				deliveries.lastDelivered(provider.id(), since).orElse(null),
				provider.suspended());
	}

	@Transactional(readOnly = true)
	public WebhookSettings webhookSettings(String portalUser) {
		Provider provider = approved(portalUser);
		return settings(provider, endpoint(provider));
	}

	/** A new URL or events: deliveries created from now on follow them; those already made keep theirs. */
	@Transactional
	public WebhookSettings updateWebhookSettings(String portalUser, String url, Set<WebhookEvent> events) {
		Provider provider = approved(portalUser);
		URI checked = webhookUrls.check("url", url);
		WebhookEndpoint updated = endpoint(provider).withSettings(checked, events);
		endpoints.save(updated);
		return settings(provider, updated);
	}

	/** A new signing secret, shown once. The old one is signed with too for 24 hours. */
	@Transactional
	public String rotateWebhookSecret(String portalUser) {
		Provider provider = approved(portalUser);
		Instant now = clock.instant();
		Provider rotated = provider.withWebhookSecretIssuedAt(now);
		String secret = WebhookSecretGenerator.next();
		endpoints.save(endpoint(provider).rotated(secrets.store(provider.id(), secret), now.plus(SECRET_OVERLAP)));
		if (!providers.update(provider, rotated)) {
			throw concurrentUpdate();
		}
		return secret;
	}

	/** {@code ping} to the endpoint now, whatever it subscribed to. */
	@Transactional
	public DeliveryView sendTestEvent(String portalUser) {
		Provider provider = approved(portalUser);
		return view(webhooks.sendPing(provider.id()), endpoint(provider));
	}

	/** A page of the last 30 days' deliveries, newest first, in {@code status} or any. */
	@Transactional(readOnly = true)
	public DeliveryPage deliveries(String portalUser, Delivery.Status status, int page, int size) {
		Provider provider = approved(portalUser);
		WebhookEndpoint endpoint = endpoint(provider);
		Instant since = clock.instant().minus(LOG_PERIOD);
		List<DeliveryView> items = deliveries.page(provider.id(), status, since, page, size).stream()
				.map(delivery -> view(delivery, endpoint))
				.toList();
		return new DeliveryPage(items, page, size, deliveries.count(provider.id(), status, since));
	}

	@Transactional(readOnly = true)
	public DeliveryView delivery(String portalUser, String deliveryId) {
		Provider provider = approved(portalUser);
		return view(delivery(provider, deliveryId), endpoint(provider));
	}

	/** One more attempt at a failed delivery, with the same id and body. */
	@Transactional
	public DeliveryView resend(String portalUser, String deliveryId) {
		Provider provider = approved(portalUser);
		delivery(provider, deliveryId);
		return view(webhooks.resend(provider.id(), deliveryId), endpoint(provider));
	}

	private Provider approved(String portalUser) {
		return providers.findByPortalUser(portalUser).orElseThrow(ProviderNotApproved::new);
	}

	private WebhookEndpoint endpoint(Provider provider) {
		return endpoints.find(provider.id())
				.orElseThrow(() -> new IllegalStateException("Provider " + provider.id() + " has no webhook endpoint"));
	}

	private Delivery delivery(Provider provider, String deliveryId) {
		return deliveries.find(deliveryId)
				.filter(found -> found.providerId() == provider.id())
				.orElseThrow(() -> new DeliveryNotFound(deliveryId));
	}

	private WebhookSettings settings(Provider provider, WebhookEndpoint endpoint) {
		return new WebhookSettings(endpoint.url(), endpoint.events(),
				Provider.hintOf(secrets.reveal(endpoint.signingSecret())), provider.webhookSecretIssuedAt());
	}

	/** The delivery with its attempts, and the headers as the last attempt sent them (or the first will). */
	private DeliveryView view(Delivery delivery, WebhookEndpoint endpoint) {
		List<DeliveryAttempt> attempts = deliveries.attempts(delivery.id());
		Instant signedAt = attempts.isEmpty() ? clock.instant() : attempts.getLast().at();
		return new DeliveryView(delivery, attempts, webhooks.signedHeaders(delivery, endpoint, signedAt));
	}

	private static BusinessRuleViolation concurrentUpdate() {
		return new BusinessRuleViolation("CONCURRENT_UPDATE", "The provider changed meanwhile. Refresh and retry.");
	}

	/** The application, and whether its approved provider is suspended. */
	public record MyApplication(ProviderApplication application, boolean suspended) {
	}

	/** The secrets, shown once. */
	public record IssuedCredentials(String clientId, String clientSecret, String webhookSecret) {
	}

	public record CredentialsSummary(String clientId, String secretHint, Instant secretIssuedAt, Set<String> scopes,
			String tokenUrl) {
	}

	public record IntegrationHealth(long delivered, long retrying, long failed, Instant lastDeliveredAt,
			boolean suspended) {
	}

	public record WebhookSettings(URI url, Set<WebhookEvent> events, String secretHint, Instant secretIssuedAt) {
	}

	public record DeliveryView(Delivery delivery, List<DeliveryAttempt> attempts, Map<String, String> headers) {
	}

	public record DeliveryPage(List<DeliveryView> items, int page, int size, long totalElements) {
	}

	/** No delivery with that id was made to this provider: 404 {@code DELIVERY_NOT_FOUND}. */
	public static class DeliveryNotFound extends ResourceNotFound {

		public DeliveryNotFound(String deliveryId) {
			super("Webhook delivery", deliveryId);
		}
	}
}
