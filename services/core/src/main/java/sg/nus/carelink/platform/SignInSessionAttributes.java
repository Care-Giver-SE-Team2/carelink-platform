package sg.nus.carelink.platform;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.platform.security.SignedInUserSession;

/**
 * After a successful sign-in, writes the account id and display name into the session next to the
 * security context. The other services read them from the shared session ({@code SignedInUsers})
 * instead of asking core who is signed in.
 *
 * <p>A filter on {@code POST /api/auth/login}, so the sign-in code itself stays as it is. It runs
 * inside the session filter, so the two attributes are saved with the session.
 */
@Configuration(proxyBeanMethods = false)
class SignInSessionAttributes {

	static final String SIGN_IN_PATH = "/api/auth/login";

	@Bean
	FilterRegistrationBean<OncePerRequestFilter> signInSessionAttributesFilter(IdentityService identities) {
		FilterRegistrationBean<OncePerRequestFilter> registration =
				new FilterRegistrationBean<>(new RememberSignedInUser(identities));
		registration.addUrlPatterns(SIGN_IN_PATH);
		return registration;
	}

	static class RememberSignedInUser extends OncePerRequestFilter {

		private final IdentityService identities;

		RememberSignedInUser(IdentityService identities) {
			this.identities = identities;
		}

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException {
			chain.doFilter(request, response);
			if (!"POST".equals(request.getMethod()) || response.getStatus() != HttpServletResponse.SC_OK) {
				return;
			}
			HttpSession session = request.getSession(false);
			if (session == null
					|| !(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
							instanceof SecurityContext context)) {
				return;
			}
			Authentication signedIn = context.getAuthentication();
			if (signedIn == null || !signedIn.isAuthenticated()) {
				return;
			}
			AppUser user = identities.require(signedIn.getName());
			SignedInUserSession.remember(session, user.id(), user.displayName());
		}

	}

}
