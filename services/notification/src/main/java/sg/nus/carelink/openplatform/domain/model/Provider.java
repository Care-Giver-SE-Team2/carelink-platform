package sg.nus.carelink.openplatform.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * An approved Open Platform provider: who it is, the services it offers, its OAuth2 client and its
 * standing. A provider is ACTIVE, or SUSPENDED by a manager: then it is dispatched nothing, its tokens
 * are refused by the job API, and its webhooks stop, until it is reinstated. A manager may also remove it,
 * from either: REMOVED is for good. Its client is deleted, its webhooks stop, and nothing about it changes
 * again; its record stays, for the jobs and deliveries that name it. Its email is free to apply again,
 * and a new application approved makes a new provider (docs/section5/decisions.md, D19).
 *
 * <p>Its secrets are never kept as themselves. The client secret and the webhook signing secret are
 * held sealed only between approval and the provider's collecting them, once; afterwards only the
 * client secret's last four characters remain, to show which one is in use. The signing secret itself
 * stays sealed on the webhook endpoint, which signs with it.
 *
 * <p>A provider takes the id of the application it was approved from.
 *
 * <p>{@code version} is {@code ProviderUpdated}'s: it goes up by one with every change report hears of.
 *
 * <p>Must not import JPA or Spring Data; ArchUnit rejects the build if it does.
 */
public record Provider(
		long id,
		String organisationName,
		String contactEmail,
		List<String> services,
		String portalUserId,
		String clientId,
		String clientSecretHint,
		Instant clientSecretIssuedAt,
		String uncollectedClientSecret,
		String uncollectedWebhookSecret,
		Instant webhookSecretIssuedAt,
		Instant credentialsCollectedAt,
		Status status,
		int version,
		Instant approvedAt) {

	public enum Status {
		ACTIVE, SUSPENDED, REMOVED
	}

	public Provider {
		Objects.requireNonNull(organisationName, "organisationName");
		Objects.requireNonNull(contactEmail, "contactEmail");
		services = List.copyOf(services);
		Objects.requireNonNull(portalUserId, "portalUserId");
		Objects.requireNonNull(clientId, "clientId");
		Objects.requireNonNull(clientSecretHint, "clientSecretHint");
		Objects.requireNonNull(clientSecretIssuedAt, "clientSecretIssuedAt");
		Objects.requireNonNull(webhookSecretIssuedAt, "webhookSecretIssuedAt");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(approvedAt, "approvedAt");
		if ((credentialsCollectedAt == null) != (uncollectedClientSecret != null && uncollectedWebhookSecret != null)) {
			throw new IllegalArgumentException("A provider holds its sealed secrets exactly until it collects them");
		}
	}

	/**
	 * The provider an application becomes, active, holding its first secrets sealed for collection.
	 *
	 * @param sealedClientSecret the client secret, sealed
	 * @param sealedWebhookSecret the signing secret, sealed
	 */
	public static Provider approved(ProviderApplication application, String clientId, String clientSecretHint,
			String sealedClientSecret, String sealedWebhookSecret, Instant now) {
		ApplicationDetails details = application.details();
		return new Provider(application.id(), details.organisationName(), details.contactEmail(),
				details.services(), application.portalUserId(), clientId, clientSecretHint, now, sealedClientSecret,
				sealedWebhookSecret, now, null, Status.ACTIVE, 0, now);
	}

	public boolean suspended() {
		return status == Status.SUSPENDED;
	}

	public boolean removed() {
		return status == Status.REMOVED;
	}

	public boolean collected() {
		return credentialsCollectedAt != null;
	}

	/** The provider once it has collected its secrets: they are not kept any longer. */
	public Provider collectedAt(Instant at) {
		requireNotRemoved();
		if (collected()) {
			throw new CredentialsAlreadyCollected();
		}
		return new Provider(id, organisationName, contactEmail, services, portalUserId, clientId,
				clientSecretHint, clientSecretIssuedAt, null, null, webhookSecretIssuedAt, at, status, version,
				approvedAt);
	}

	/** A new client secret, perhaps with a new client: report hears of it, as the token names the client. */
	public Provider withClient(String newClientId, String newSecretHint, Instant at) {
		requireNotRemoved();
		requireCollected();
		return new Provider(id, organisationName, contactEmail, services, portalUserId, newClientId,
				newSecretHint, at, null, null, webhookSecretIssuedAt, credentialsCollectedAt, status, version + 1,
				approvedAt);
	}

	/** A new webhook signing secret. Nothing report keeps changes, so the version does not. */
	public Provider withWebhookSecretIssuedAt(Instant at) {
		requireNotRemoved();
		requireCollected();
		return new Provider(id, organisationName, contactEmail, services, portalUserId, clientId,
				clientSecretHint, clientSecretIssuedAt, null, null, at, credentialsCollectedAt, status, version,
				approvedAt);
	}

	/**
	 * The services the provider offers from now on: names from report's catalogue, each once, at least one.
	 * report hears of it, as it dispatches by them; a job already offered stays offered.
	 */
	public Provider withServices(List<String> next) {
		requireNotRemoved();
		List<String> cleaned = next.stream().map(String::strip).distinct().toList();
		if (cleaned.isEmpty() || cleaned.stream().anyMatch(String::isEmpty)) {
			throw new InvalidField("services", "Choose at least one service.");
		}
		return new Provider(id, organisationName, contactEmail, cleaned, portalUserId, clientId,
				clientSecretHint, clientSecretIssuedAt, uncollectedClientSecret, uncollectedWebhookSecret,
				webhookSecretIssuedAt, credentialsCollectedAt, status, version + 1, approvedAt);
	}

	public Provider suspend() {
		requireNotRemoved();
		if (suspended()) {
			throw new BusinessRuleViolation("ALREADY_SUSPENDED", "This provider is already suspended.");
		}
		return withStatus(Status.SUSPENDED);
	}

	public Provider reinstate() {
		requireNotRemoved();
		if (!suspended()) {
			throw new BusinessRuleViolation("NOT_SUSPENDED", "This provider is not suspended.");
		}
		return withStatus(Status.ACTIVE);
	}

	/** The provider removed for good, whether active or suspended. */
	public Provider remove() {
		requireNotRemoved();
		return withStatus(Status.REMOVED);
	}

	private Provider withStatus(Status next) {
		return new Provider(id, organisationName, contactEmail, services, portalUserId, clientId,
				clientSecretHint, clientSecretIssuedAt, uncollectedClientSecret, uncollectedWebhookSecret,
				webhookSecretIssuedAt, credentialsCollectedAt, next, version + 1, approvedAt);
	}

	/** Nothing about a removed provider changes again. */
	private void requireNotRemoved() {
		if (removed()) {
			throw new ProviderRemoved();
		}
	}

	/** Rotating needs the first secrets collected: until then, collecting them is the way to get them. */
	private void requireCollected() {
		if (!collected()) {
			throw new BusinessRuleViolation("CREDENTIALS_NOT_COLLECTED",
					"Collect the credentials first; rotating is for after.");
		}
	}

	/** The last four characters of a secret: enough to tell which one is in use, too few to use. */
	public static String hintOf(String secret) {
		return secret.substring(Math.max(0, secret.length() - 4));
	}
}
