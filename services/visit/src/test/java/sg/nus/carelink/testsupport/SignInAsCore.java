package sg.nus.carelink.testsupport;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import sg.nus.carelink.platform.security.SignedInUserSession;
import tools.jackson.databind.json.JsonMapper;

/**
 * core's sign-in, for visit's tests. In a real run the browser signs in through core and visit reads
 * the session core leaves behind; here nothing of core runs, so this signs the tests' people in the
 * same way, from the accounts the tests lay out in core's tables: the security context in the session,
 * and the signed-in user's id and name beside it (SignInSessionAttributes in core). It answers core's
 * paths as core does - the CSRF cookie, sign-in, who is signed in, sign-out - so the tests sign in as
 * a browser does. Whether a sign-in carries its CSRF token is core's to check, and this does not.
 *
 * <p>A filter in front of the service's security chain, not a controller behind it: a service lets
 * nobody sign in, and a second chain would turn the platform's own chain off. Registered by
 * {@link CoreStandInConfiguration}; test code only.
 */
class SignInAsCore extends OncePerRequestFilter {

	private static final PasswordEncoder PASSWORDS = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();

	private final JdbcTemplate jdbc;

	SignInAsCore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/auth/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException {
		String path = request.getRequestURI();
		String method = request.getMethod();
		if ("GET".equals(method) && path.equals("/api/auth/csrf")) {
			csrfTokens.saveToken(csrfTokens.generateToken(request), request, response);
			response.setStatus(HttpServletResponse.SC_OK);
		}
		else if ("GET".equals(method) && path.equals("/api/auth/me")) {
			signedIn(request, response);
		}
		else if ("POST".equals(method) && path.equals("/api/auth/login")) {
			signIn(request, response);
		}
		else if ("POST".equals(method) && path.equals("/api/auth/logout")) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
			// core's sign-out deletes the session cookie as well
			Cookie expired = new Cookie("JSESSIONID", null);
			expired.setPath("/");
			expired.setMaxAge(0);
			response.addCookie(expired);
			response.setStatus(HttpServletResponse.SC_NO_CONTENT);
		}
		else {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
		}
	}

	private void signIn(HttpServletRequest request, HttpServletResponse response) throws IOException {
		@SuppressWarnings("unchecked")
		Map<String, String> credentials = JSON.readValue(request.getInputStream(), Map.class);
		List<Map<String, Object>> accounts = jdbc.queryForList(
				"select id, display_name, password_hash from app_user where username = ? and enabled = true",
				credentials.get("username"));
		if (accounts.isEmpty() || credentials.get("password") == null
				|| !PASSWORDS.matches(credentials.get("password"), (String) accounts.getFirst().get("password_hash"))) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		String username = credentials.get("username");
		long id = ((Number) accounts.getFirst().get("id")).longValue();
		String displayName = (String) accounts.getFirst().get("display_name");
		List<String> roles = jdbc.queryForList("select role from user_role where user_id = ? order by role",
				String.class, id);

		SecurityContext context = new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(username,
				null, roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
		HttpSession session = request.getSession(true);
		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
		SignedInUserSession.remember(session, id, displayName);

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		JSON.writeValue(response.getOutputStream(),
				Map.of("id", id, "username", username, "displayName", displayName, "roles", roles));
	}

	/** Who the session belongs to, as core's /api/auth/me answers; 401 without one. */
	private void signedIn(HttpServletRequest request, HttpServletResponse response) throws IOException {
		HttpSession session = request.getSession(false);
		Object id = session == null ? null : session.getAttribute(SignedInUserSession.ID);
		if (id == null) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		Map<String, Object> account = jdbc.queryForMap("select username, display_name from app_user where id = ?", id);
		List<String> roles = jdbc.queryForList("select role from user_role where user_id = ? order by role",
				String.class, id);
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		JSON.writeValue(response.getOutputStream(), Map.of("id", id, "username", account.get("username"),
				"displayName", account.get("display_name"), "roles", roles));
	}

}
