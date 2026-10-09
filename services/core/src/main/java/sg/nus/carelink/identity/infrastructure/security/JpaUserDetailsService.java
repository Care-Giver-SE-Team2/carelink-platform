package sg.nus.carelink.identity.infrastructure.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Translates a stored account into the UserDetails that Spring Security understands.
 *
 * <p>It is injected into shared.security.SecurityConfig by type; SecurityConfig does not
 * import this class. That is what keeps shared from depending on identity, so no cycle
 * forms between modules.
 */
@Service
class JpaUserDetailsService implements UserDetailsService {

	private final AuthenticationQuery query;

	JpaUserDetailsService(AuthenticationQuery query) {
		this.query = query;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		AuthenticationQuery.Credentials credentials = query.findCredentials(username)
				.orElseThrow(() -> new UsernameNotFoundException("No such account: " + username));
		return User.withUsername(credentials.username())
				.password(credentials.passwordHash())
				.authorities(credentials.authorities().toArray(String[]::new))
				.disabled(!credentials.enabled())
				.build();
	}
}
