package sg.nus.carelink.report.support;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import sg.nus.carelink.platform.security.SignedInUserSession;

/**
 * The session core leaves behind when someone signs in, for report's integration tests: report
 * never signs anyone in, it reads the login core keeps in the shared session store. The session
 * holds the security context with the account's roles, and the account id and display name core
 * writes ({@link SignedInUserSession}). The account is read from the {@code app_user} and
 * {@code user_role} rows the test seeded into the shared schema.
 */
public final class SignedInSessions {

	private final JdbcTemplate jdbc;

	public SignedInSessions(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** {@code username}'s session after signing in through core. */
	public MockHttpSession of(String username) {
		Map<String, Object> account = jdbc.queryForMap("select id, display_name from app_user where username = ?",
				username);
		long id = ((Number) account.get("id")).longValue();
		List<SimpleGrantedAuthority> roles = jdbc.queryForList("select role from user_role where user_id = ?",
				String.class, id).stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
				new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(username, null, roles)));
		SignedInUserSession.remember(session, id, (String) account.get("display_name"));
		return session;
	}

}
