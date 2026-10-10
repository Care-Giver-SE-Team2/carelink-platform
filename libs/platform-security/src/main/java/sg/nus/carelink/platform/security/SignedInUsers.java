package sg.nus.carelink.platform.security;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Tells a service who is signed in, without asking core: the username and roles come from the
 * security context, the account id and display name from the session attributes core wrote at
 * sign-in ({@link SignedInUserSession}).
 */
public class SignedInUsers {

	private static final String ROLE_PREFIX = "ROLE_";

	/** The signed-in user, or empty for an anonymous request. */
	public Optional<SignedInUser> current() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return Optional.empty();
		}
		HttpSession session = session();
		Long id = session == null ? null : (Long) session.getAttribute(SignedInUserSession.ID);
		String displayName = session == null ? null : (String) session.getAttribute(SignedInUserSession.DISPLAY_NAME);
		Set<String> roles = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.map(role -> role.startsWith(ROLE_PREFIX) ? role.substring(ROLE_PREFIX.length()) : role)
				.collect(Collectors.toUnmodifiableSet());
		return Optional.of(new SignedInUser(id, authentication.getName(), displayName, roles));
	}

	/**
	 * The signed-in user with their account id. Answers 401 when there is none, including a login
	 * made before core started writing the id into the session: signing in again fixes that.
	 */
	public SignedInUser require() {
		return current()
				.filter(user -> user.id() != null)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Nobody is signed in"));
	}

	private static HttpSession session() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (attributes instanceof ServletRequestAttributes servlet) {
			HttpServletRequest request = servlet.getRequest();
			return request.getSession(false);
		}
		return null;
	}

}
