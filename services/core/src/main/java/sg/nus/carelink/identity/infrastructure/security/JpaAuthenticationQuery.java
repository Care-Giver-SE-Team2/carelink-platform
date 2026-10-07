package sg.nus.carelink.identity.infrastructure.security;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the two tables directly with JdbcClient rather than through JPA. Authentication
 * is one of the hottest queries on the request path, and the field it needs — the password
 * hash — is deliberately not part of what the JPA entity exposes to the rest of the module.
 */
@Component
class JpaAuthenticationQuery implements AuthenticationQuery {

	private final JdbcClient jdbc;

	JpaAuthenticationQuery(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Credentials> findCredentials(String username) {
		Optional<Row> row = jdbc.sql("""
						SELECT id, username, password_hash, enabled
						  FROM app_user
						 WHERE username = :username
						""")
				.param("username", username)
				.query(Row.class)
				.optional();

		return row.map(r -> {
			List<String> roles = jdbc.sql("SELECT role FROM user_role WHERE user_id = :id")
					.param("id", r.id())
					.query(String.class)
					.list();
			Set<String> authorities = roles.stream().map(role -> "ROLE_" + role).collect(java.util.stream.Collectors.toUnmodifiableSet());
			return new Credentials(r.username(), r.passwordHash(), r.enabled(), authorities);
		});
	}

	record Row(Long id, String username, String passwordHash, boolean enabled) {
	}
}
