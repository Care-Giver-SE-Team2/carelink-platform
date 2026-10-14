package sg.nus.carelink.openplatform.application;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.openplatform.domain.model.Provider;
import sg.nus.carelink.openplatform.domain.model.ProviderApplication;
import sg.nus.carelink.openplatform.domain.model.WebhookEndpoint;
import sg.nus.carelink.openplatform.domain.model.WebhookEvent;
import sg.nus.carelink.openplatform.domain.repository.ProviderApplications;
import sg.nus.carelink.openplatform.domain.repository.Providers;
import sg.nus.carelink.openplatform.domain.repository.WebhookEndpoints;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * A CareLink manager's side of the Open Platform: deciding applications, and suspending and
 * reinstating providers. Internal, behind core's session; the public API has none of it.
 *
 * <p>Approving makes the provider whole at once: its app client and secret, its webhook endpoint at
 * the URL it applied with, subscribed to every job event, with a new signing secret. Both secrets are
 * kept sealed until the provider collects them from the portal, once. report hears of the provider
 * ({@code ProviderUpdated}) in the same transaction.
 */
@Service
public class ProviderReviewService {

	private final ProviderApplications applications;
	private final Providers providers;
	private final WebhookEndpoints endpoints;
	private final ProviderIdentities identities;
	private final SigningSecrets secrets;
	private final ProviderEvents events;
	private final Clock clock;

	public ProviderReviewService(ProviderApplications applications, Providers providers, WebhookEndpoints endpoints,
			ProviderIdentities identities, SigningSecrets secrets, ProviderEvents events, Clock clock) {
		this.applications = applications;
		this.providers = providers;
		this.endpoints = endpoints;
		this.identities = identities;
		this.secrets = secrets;
		this.events = events;
		this.clock = clock;
	}

	/** The applications waiting for a decision, newest first. */
	@Transactional(readOnly = true)
	public List<ProviderApplication> pending() {
		return applications.pending();
	}

	/** Every approved provider, the most recently approved first. */
	@Transactional(readOnly = true)
	public List<Provider> providers() {
		return providers.all();
	}

	/** The provider with the application it was approved from and where its webhooks go now. */
	@Transactional(readOnly = true)
	public ProviderDetails details(Provider provider) {
		return new ProviderDetails(provider, application(provider.id()),
				endpoints.find(provider.id()).map(WebhookEndpoint::url).orElse(null));
	}

	/**
	 * Approves the application and creates its provider.
	 *
	 * <p>The app client is made in this transaction too, so should it fail (another manager decided
	 * first), no client is left behind.
	 */
	@Transactional
	public Provider approve(long applicationId, long managerId) {
		ProviderApplication application = application(applicationId);
		if (application.status() != ProviderApplication.Status.PENDING) {
			throw alreadyDecided();
		}
		Instant now = clock.instant();
		long providerId = application.id();
		ProviderIdentities.ClientCredentials client = identities.createClient(providerId,
				application.details().organisationName());
		String webhookSecret = WebhookSecretGenerator.next();
		String sealedWebhookSecret = secrets.store(providerId, webhookSecret);
		Provider provider = providers.create(Provider.approved(application, client.clientId(),
				Provider.hintOf(client.clientSecret()), secrets.store(providerId, client.clientSecret()),
				sealedWebhookSecret, now));
		endpoints.save(WebhookEndpoint.of(providerId, application.details().webhookUrl(),
				Set.of(WebhookEvent.JOB_DISPATCHED, WebhookEvent.JOB_CANCELLED), sealedWebhookSecret));
		if (!applications.decide(application.approved(providerId, managerId, now))) {
			throw alreadyDecided();
		}
		events.changed(provider, now);
		return provider;
	}

	/** Rejects the application; the provider is shown {@code reason} and may apply again. */
	@Transactional
	public ProviderApplication reject(long applicationId, String reason, long managerId) {
		ProviderApplication rejected = application(applicationId).rejected(reason, managerId, clock.instant());
		if (!applications.decide(rejected)) {
			throw alreadyDecided();
		}
		return rejected;
	}

	/** Stops the provider: no jobs, no tokens accepted by the job API, no webhooks. */
	@Transactional
	public Provider suspend(long providerId) {
		Provider provider = provider(providerId);
		return standing(provider, provider.suspend(), false);
	}

	@Transactional
	public Provider reinstate(long providerId) {
		Provider provider = provider(providerId);
		return standing(provider, provider.reinstate(), true);
	}

	private Provider standing(Provider before, Provider after, boolean sending) {
		if (!providers.update(before, after)) {
			throw new BusinessRuleViolation("CONCURRENT_UPDATE", "The provider changed meanwhile. Refresh and retry.");
		}
		endpoints.find(after.id()).ifPresent(endpoint -> endpoints.save(endpoint.withEnabled(sending)));
		events.changed(after, clock.instant());
		return after;
	}

	private ProviderApplication application(long id) {
		return applications.find(id).orElseThrow(() -> new ResourceNotFound("Provider application", id));
	}

	private Provider provider(long id) {
		return providers.find(id).orElseThrow(() -> new ResourceNotFound("Provider", id));
	}

	/** What the manager's screen shows of a provider: never a secret. */
	public record ProviderDetails(Provider provider, ProviderApplication application, URI webhookUrl) {
	}

	private static BusinessRuleViolation alreadyDecided() {
		return new BusinessRuleViolation("ALREADY_DECIDED", "This application has already been decided.");
	}
}
