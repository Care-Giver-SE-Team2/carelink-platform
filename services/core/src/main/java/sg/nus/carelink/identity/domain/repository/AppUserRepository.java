package sg.nus.carelink.identity.domain.repository;

import sg.nus.carelink.identity.domain.model.AppUser;

import java.util.Optional;

/**
 * Repository interface. The domain layer declares what it needs; the implementation
 * lives in infrastructure.persistence. This is dependency inversion.
 *
 * <p>It is precisely why the domain layer can be unit tested without a database:
 * a test supplies a hand-written fake implementation.
 */
public interface AppUserRepository {

	Optional<AppUser> findByUsername(String username);

	Optional<AppUser> findById(Long id);

	/**
	 * Stores a new account and returns it with its id. The password hash is passed straight
	 * through to storage; AppUser never holds it.
	 */
	AppUser add(AppUser user, String passwordHash);
}
