package sg.nus.carelink.identity.infrastructure.security;

import java.util.Optional;
import java.util.Set;

/**
 * A narrow read port used only by authentication.
 *
 * <p>Why not reuse the domain's AppUserRepository? Because the password hash is
 * deliberately absent from the domain model: it is an implementation detail of the
 * authentication mechanism. Giving authentication its own narrow interface to fetch
 * what it needs is what keeps the domain model clean.
 */
interface AuthenticationQuery {

	Optional<Credentials> findCredentials(String username);

	record Credentials(String username, String passwordHash, boolean enabled, Set<String> authorities) {
	}
}
