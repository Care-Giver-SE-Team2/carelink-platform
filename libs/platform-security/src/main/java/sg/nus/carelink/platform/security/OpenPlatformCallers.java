package sg.nus.carelink.platform.security;

import java.util.Optional;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Tells an {@code /api/open/**} controller who is calling, as {@link SignedInUsers} does for the app's own
 * paths. The token was checked by {@link OpenPlatformSecurityConfiguration} before the controller ran.
 */
public class OpenPlatformCallers {

	/** The caller, or empty when the request carried no token (an open path, such as applying). */
	public Optional<OpenPlatformCaller> current() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken token) {
			return Optional.of(OpenPlatformTokens.caller(token.getToken()));
		}
		return Optional.empty();
	}

	/** The person signed in to the Partner Portal. Answers 401 for anyone else. */
	public OpenPlatformCaller requirePortalUser() {
		return current()
				.filter(OpenPlatformCaller::portalUser)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No portal user"));
	}

	/** The provider's server. Answers 401 for anyone else. */
	public OpenPlatformCaller requireClient() {
		return current()
				.filter(caller -> !caller.portalUser())
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No provider client"));
	}
}
