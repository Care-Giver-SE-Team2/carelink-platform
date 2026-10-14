package sg.nus.carelink.platform.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * The Open Platform's answers to a refused token, as problem details with the contract's stable
 * {@code code} (docs/api/open-platform.yaml, {@code Unauthorised} and {@code JobsForbidden}). Partners
 * branch on the code, so these are written here rather than left to the servlet container's error page.
 */
final class OpenPlatformProblems {

	private OpenPlatformProblems() {
	}

	/** 401 {@code INVALID_TOKEN}: no token, or one that is malformed, expired or not CareLink's. */
	static AuthenticationEntryPoint invalidToken() {
		return (request, response, failure) -> {
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
			write(response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
					"The access token is missing, malformed or expired.");
		};
	}

	/** 403 {@code INSUFFICIENT_SCOPE}: a good token, not allowed this path. */
	static AccessDeniedHandler insufficientScope() {
		return (request, response, denied) -> {
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"");
			write(response, HttpStatus.FORBIDDEN, "INSUFFICIENT_SCOPE", "The access token does not allow this request.");
		};
	}

	/** The fixed texts above need no JSON escaping. */
	private static void write(HttpServletResponse response, HttpStatus status, String code, String detail)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType("application/problem+json");
		response.getWriter().write("""
				{"type":"about:blank","title":"%s","status":%d,"detail":"%s","code":"%s"}"""
				.formatted(status.getReasonPhrase(), status.value(), detail, code));
	}
}
