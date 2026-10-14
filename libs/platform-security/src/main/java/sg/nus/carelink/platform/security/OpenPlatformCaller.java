package sg.nus.carelink.platform.security;

import java.util.Set;

/**
 * Who is calling an {@code /open/**} path, from their bearer token (docs/api/open-platform.yaml,
 * Authentication). Two kinds: a person at a provider, signed in to the Partner Portal through the user
 * pool, and a provider's own server, with the client credentials it collected.
 *
 * @param subject the token's {@code sub}: the portal user's id in the user pool, or for a server the
 *        client id again
 * @param username the portal user's {@code username}; null for a server
 * @param clientId the token's {@code client_id}: the app client the token was issued to. For a server
 *        this names the provider; for a portal user it is the Partner Portal's own client
 * @param scopes the token's scopes, such as {@code open-platform/jobs.read}
 */
public record OpenPlatformCaller(String subject, String username, String clientId, Set<String> scopes) {

	public OpenPlatformCaller {
		scopes = Set.copyOf(scopes);
	}

	/** A person signed in to the Partner Portal, not a provider's server. */
	public boolean portalUser() {
		return username != null;
	}
}
