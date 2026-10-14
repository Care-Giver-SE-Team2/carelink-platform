package sg.nus.carelink.platform.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * How an Open Platform token is read: the claims notification puts in the access tokens it issues,
 * named as an OAuth2 provider such as Cognito names them.
 *
 * <ul>
 * <li>A portal user's token has {@code username}; it is given {@link #PORTAL_USER} and no scopes.</li>
 * <li>A provider server's token, from the client-credentials grant, has none; it is given
 * {@link #CLIENT} and a {@code SCOPE_} authority for each scope it was granted.</li>
 * </ul>
 * Only access tokens are taken ({@code token_use} is {@code access}): an ID token, which a portal user
 * also gets on signing in, is refused.
 */
public final class OpenPlatformTokens {

	/** A person signed in to the Partner Portal. */
	public static final String PORTAL_USER = "OPEN_PLATFORM_PORTAL_USER";

	/** A provider's server, with its client credentials. */
	public static final String CLIENT = "OPEN_PLATFORM_CLIENT";

	/** Reading the jobs dispatched to the provider. */
	public static final String JOBS_READ = "open-platform/jobs.read";

	/** Accepting, declining and completing them. */
	public static final String JOBS_WRITE = "open-platform/jobs.write";

	private OpenPlatformTokens() {
	}

	/** The authentication a checked token becomes; its name is the username or the client id. */
	static AbstractAuthenticationToken authentication(Jwt jwt) {
		OpenPlatformCaller caller = caller(jwt);
		List<GrantedAuthority> authorities = new ArrayList<>();
		if (caller.portalUser()) {
			authorities.add(new SimpleGrantedAuthority(PORTAL_USER));
		}
		else {
			authorities.add(new SimpleGrantedAuthority(CLIENT));
			caller.scopes().forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
		}
		return new JwtAuthenticationToken(jwt, authorities,
				caller.portalUser() ? caller.username() : caller.clientId());
	}

	static OpenPlatformCaller caller(Jwt jwt) {
		String scope = jwt.getClaimAsString("scope");
		Set<String> scopes = scope == null ? Set.of()
				: Arrays.stream(scope.split(" ")).filter(s -> !s.isBlank()).collect(Collectors.toSet());
		return new OpenPlatformCaller(jwt.getSubject(), jwt.getClaimAsString("username"),
				jwt.getClaimAsString("client_id"), scopes);
	}

	/** Refuses anything but an access token, and a token with no {@code client_id}. */
	public static OAuth2TokenValidator<Jwt> accessTokensOnly() {
		return jwt -> "access".equals(jwt.getClaimAsString("token_use")) && jwt.getClaimAsString("client_id") != null
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Not an access token", null));
	}
}
