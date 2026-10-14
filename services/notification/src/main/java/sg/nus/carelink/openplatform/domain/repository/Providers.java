package sg.nus.carelink.openplatform.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.openplatform.domain.model.Provider;

/** The approved Open Platform providers. */
public interface Providers {

	/** Keeps a newly approved provider, under the id of its application. */
	Provider create(Provider provider);

	Optional<Provider> find(long id);

	Optional<Provider> findByPortalUser(String portalUserId);

	/** Every provider, the most recently approved first. */
	List<Provider> all();

	/**
	 * Moves the provider from {@code before} to {@code after}, only if it is still as {@code before}
	 * left it: same version, secrets not yet collected or collected alike.
	 *
	 * @return false when it changed meanwhile, and nothing was written
	 */
	boolean update(Provider before, Provider after);
}
