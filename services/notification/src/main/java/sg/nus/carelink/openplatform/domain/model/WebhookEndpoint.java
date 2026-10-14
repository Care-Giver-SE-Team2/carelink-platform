package sg.nus.carelink.openplatform.domain.model;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Where a provider takes its webhooks: the URL, the job events it subscribed to, and its signing
 * secret, sealed: as {@code SigningSecrets} encrypted it, never as itself. For a day after a
 * rotation the secret before it is kept too, and CareLink signs with both (docs/api/webhooks.md,
 * section 5). A disabled endpoint belongs to a suspended provider and is sent nothing.
 *
 * <p>Must not import JPA or Spring Data; ArchUnit rejects the build if it does.
 */
public record WebhookEndpoint(
		long providerId,
		URI url,
		Set<WebhookEvent> events,
		String signingSecret,
		String previousSigningSecret,
		Instant previousSecretUntil,
		boolean enabled) {

	public WebhookEndpoint {
		Objects.requireNonNull(url, "url");
		Objects.requireNonNull(signingSecret, "signingSecret");
		events = Set.copyOf(events);
		if (events.contains(WebhookEvent.PING)) {
			throw new IllegalArgumentException("ping is always sent and is not subscribed to");
		}
	}

	/** A new endpoint, enabled, with one secret. */
	public static WebhookEndpoint of(long providerId, URI url, Set<WebhookEvent> events, String signingSecret) {
		return new WebhookEndpoint(providerId, url, events, signingSecret, null, null, true);
	}

	/** The endpoint moved or its events changed: the deliveries created from now on follow it. */
	public WebhookEndpoint withSettings(URI newUrl, Set<WebhookEvent> newEvents) {
		return new WebhookEndpoint(providerId, newUrl, newEvents, signingSecret, previousSigningSecret,
				previousSecretUntil, enabled);
	}

	/**
	 * A new signing secret, sealed. The one it replaces is signed with too until {@code overlapUntil},
	 * so that the provider can deploy the new one first (docs/api/webhooks.md, section 5).
	 */
	public WebhookEndpoint rotated(String newSealedSecret, Instant overlapUntil) {
		return new WebhookEndpoint(providerId, url, events, newSealedSecret, signingSecret, overlapUntil, enabled);
	}

	/** Sent to, or not: a suspended provider's endpoint is disabled until it is reinstated. */
	public WebhookEndpoint withEnabled(boolean sending) {
		return new WebhookEndpoint(providerId, url, events, signingSecret, previousSigningSecret, previousSecretUntil,
				sending);
	}

	/** Whether {@code event} is sent here: anything subscribed to, and every ping, while enabled. */
	public boolean receives(WebhookEvent event) {
		return enabled && (event == WebhookEvent.PING || events.contains(event));
	}

	/** The secrets to sign with at {@code now}, newest first: the previous one only during its overlap. */
	public List<String> sealedSigningSecrets(Instant now) {
		List<String> sealed = new ArrayList<>(2);
		sealed.add(signingSecret);
		if (previousSigningSecret != null && previousSecretUntil != null && now.isBefore(previousSecretUntil)) {
			sealed.add(previousSigningSecret);
		}
		return sealed;
	}
}
