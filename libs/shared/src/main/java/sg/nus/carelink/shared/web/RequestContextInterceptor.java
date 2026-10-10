package sg.nus.carelink.shared.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Runs around every {@code /api} request, after Spring Security's filter chain
 * and before the controller (registered in {@link WebMvcConfig}).
 *
 * <p>Three jobs:
 * <ol>
 * <li><b>Login check.</b> Refuses the request with 401 unless an authenticated,
 *     non-anonymous user is present. Spring Security already rejects anonymous
 *     requests to {@code /api/**}, so this is defence in depth: if a path is ever
 *     opened with {@code permitAll} by mistake, the API layer still refuses it.</li>
 * <li><b>Request tagging.</b> A request id is returned to the client in
 *     {@code X-Request-Id} and placed, together with the username, in the logging
 *     context (MDC). Every log line written while the request is handled then
 *     says who did it and in which request, without each class having to add it.</li>
 * <li><b>Access log.</b> One line per completed request: method, path, status,
 *     duration. This is the raw material for the audit trail the proposal
 *     promises ("who knew what, and when").</li>
 * </ol>
 *
 * <p>Authorisation is deliberately <em>not</em> done here. Which role may call
 * which endpoint is declared next to the endpoint with {@code @PreAuthorize}
 * (method security is enabled in SecurityConfig), so the rule lives with the
 * code it protects.
 */
@Component
class RequestContextInterceptor implements HandlerInterceptor {

	static final String REQUEST_ID_HEADER = "X-Request-Id";
	static final String MDC_REQUEST_ID = "requestId";
	static final String MDC_USER = "user";
	static final String ANONYMOUS = "anonymous";

	private static final Logger log = LoggerFactory.getLogger(RequestContextInterceptor.class);
	private static final String START_ATTRIBUTE = RequestContextInterceptor.class.getName() + ".start";
	/** A client may pass its own id (useful when the front end retries); anything else is replaced. */
	private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws IOException {
		String requestId = requestId(request);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		request.setAttribute(START_ATTRIBUTE, System.nanoTime());
		MDC.put(MDC_REQUEST_ID, requestId);

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!isLoggedIn(authentication)) {
			MDC.put(MDC_USER, ANONYMOUS);
			log.warn("{} {} refused: no authenticated user", request.getMethod(), request.getRequestURI());
			// afterCompletion is not called when preHandle returns false, so clean up here.
			clearContext();
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return false;
		}
		MDC.put(MDC_USER, authentication.getName());
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		try {
			long millis = elapsedMillis(request);
			if (ex == null) {
				log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
						response.getStatus(), millis);
			} else {
				log.warn("{} {} -> {} ({} ms): {}", request.getMethod(), request.getRequestURI(),
						response.getStatus(), millis, ex.toString());
			}
		} finally {
			clearContext();
		}
	}

	static boolean isLoggedIn(Authentication authentication) {
		return authentication != null
				&& authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken);
	}

	private static String requestId(HttpServletRequest request) {
		String supplied = request.getHeader(REQUEST_ID_HEADER);
		if (supplied != null && SAFE_REQUEST_ID.matcher(supplied).matches()) {
			return supplied;
		}
		return UUID.randomUUID().toString();
	}

	private static long elapsedMillis(HttpServletRequest request) {
		Object start = request.getAttribute(START_ATTRIBUTE);
		return start instanceof Long startNanos ? (System.nanoTime() - startNanos) / 1_000_000 : -1;
	}

	private static void clearContext() {
		MDC.remove(MDC_REQUEST_ID);
		MDC.remove(MDC_USER);
	}
}
