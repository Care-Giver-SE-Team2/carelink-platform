package sg.nus.carelink.identity.application;

import sg.nus.carelink.identity.domain.model.AppUser;

import java.util.Optional;

/**
 * Cross-module contract: when another module needs account information it imports
 * this interface only, and never reaches into identity's domain or infrastructure.
 *
 * <p>This is the fourth of the four situations that justify an interface. Even with
 * a single implementation it earns its keep, because it narrows the surface visible
 * to other modules so that internal refactoring cannot break them.
 */
public interface UserDirectory {

	Optional<AppUser> findByUsername(String username);

	Optional<AppUser> findById(Long id);
}
