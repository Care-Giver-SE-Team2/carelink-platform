package sg.nus.carelink.openplatform.infrastructure.identity;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * How the Open Platform's callers get their tokens, and the keys to check them with. All three are open
 * ({@code OpenPlatformSecurityConfiguration}):
 *
 * <ul>
 * <li>{@code POST /api/open/v1/portal/sessions}: a person signs in to the Partner Portal with the email and
 * password given when applying;</li>
 * <li>{@code POST /api/open/v1/oauth2/token}: a provider's server, with the client-credentials grant
 * (RFC 6749, section 4.4);</li>
 * <li>{@code GET /api/open/v1/.well-known/jwks.json}: the public keys, which report fetches.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/open/v1")
class TokenEndpoints {

	private final JdbcProviderIdentities identities;

	TokenEndpoints(JdbcProviderIdentities identities) {
		this.identities = identities;
	}

	record SignIn(String email, String password) {
	}

	record PortalSession(String accessToken, long expiresIn, String tokenType) {
	}

	@PostMapping("/portal/sessions")
	ResponseEntity<?> signIn(@RequestBody SignIn request) {
		String email = request.email() == null ? "" : request.email().strip().toLowerCase();
		Optional<String> token;
		try {
			token = identities.signIn(email, request.password());
		}
		catch (JdbcProviderIdentities.SignInLocked locked) {
			return problem(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", locked.getMessage());
		}
		if (token.isEmpty()) {
			return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Incorrect email or password.");
		}
		return ResponseEntity.ok(new PortalSession(token.get(), identities.lifetimeSeconds(), "Bearer"));
	}

	@PostMapping(path = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	ResponseEntity<Map<String, Object>> token(@RequestHeader(name = "Authorization", required = false) String authorization,
			@RequestParam(name = "grant_type", required = false) String grantType,
			@RequestParam(required = false) String scope,
			@RequestParam(name = "client_id", required = false) String formClientId,
			@RequestParam(name = "client_secret", required = false) String formClientSecret) {
		if (!"client_credentials".equals(grantType)) {
			return oauthError(HttpStatus.BAD_REQUEST, "unsupported_grant_type");
		}
		String[] basic = basic(authorization);
		String clientId = basic != null ? basic[0] : formClientId;
		String clientSecret = basic != null ? basic[1] : formClientSecret;
		Optional<String> token;
		try {
			token = clientId == null ? Optional.empty() : identities.clientToken(clientId, clientSecret, scope);
		}
		catch (IllegalArgumentException invalidScope) {
			return oauthError(HttpStatus.BAD_REQUEST, "invalid_scope");
		}
		if (token.isEmpty()) {
			return oauthError(HttpStatus.UNAUTHORIZED, "invalid_client");
		}
		return ResponseEntity.ok(Map.of("access_token", token.get(), "expires_in", identities.lifetimeSeconds(),
				"token_type", "Bearer"));
	}

	@GetMapping("/.well-known/jwks.json")
	Map<String, Object> jwks() {
		return identities.jwks();
	}

	/** The id and secret of an {@code Authorization: Basic} header, or null when there is none. */
	private static String[] basic(String authorization) {
		if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
			return null;
		}
		try {
			String decoded = new String(Base64.getDecoder().decode(authorization.substring(6).strip()),
					StandardCharsets.UTF_8);
			int colon = decoded.indexOf(':');
			return colon < 0 ? null : new String[] { decoded.substring(0, colon), decoded.substring(colon + 1) };
		}
		catch (IllegalArgumentException notBase64) {
			return null;
		}
	}

	/** As the Open API answers a refusal: a problem with the code partners branch on. */
	private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setProperty("code", code);
		return ResponseEntity.status(status).body(problem);
	}

	/** As RFC 6749, section 5.2, answers a refused token request. */
	private static ResponseEntity<Map<String, Object>> oauthError(HttpStatus status, String error) {
		return ResponseEntity.status(status).body(Map.of("error", error));
	}
}
