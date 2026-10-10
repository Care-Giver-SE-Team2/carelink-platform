package sg.nus.carelink.platform.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * Answers 401 when a controller finds nobody signed in, for example when {@link SignedInUsers#require()}
 * meets a login without an account id. It runs before the service's own exception handlers, so a
 * catch-all handler (core's has one, and services start from a copy of it) cannot turn the answer
 * into a 500.
 *
 * <p>A resolver, not a {@code @RestControllerAdvice}: an advice class here would also be picked up by
 * core's component scan, which covers this package.
 */
final class SignedOutIsUnauthorized implements HandlerExceptionResolver, Ordered {

	@Override
	public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		if (!(ex instanceof AuthenticationException)) {
			return null;
		}
		try {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		}
		catch (IOException clientGone) {
			// Nothing more can be sent to a client that has gone
		}
		return new ModelAndView();
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

}
