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
