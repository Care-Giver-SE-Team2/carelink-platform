package sg.nus.carelink.identity.application;

import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.identity.domain.repository.AppUserRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A hand-written in-memory fake of the repository.
 *
 * <p>This is what the design buys you: because the domain layer declares the repository
 * interface and the persistence layer implements it, a test can drop in a class like this
 * and exercise the application layer without starting Spring or a database. Module tests
 * should follow the same pattern.
 */
public class InMemoryAppUserRepository implements AppUserRepository {

	private final List<AppUser> users = new ArrayList<>();
	private final Map<String, String> passwordHashes = new HashMap<>();

	public InMemoryAppUserRepository with(AppUser user) {
		users.add(user);
		return this;
	}

	@Override
	public Optional<AppUser> findByUsername(String username) {
		return users.stream().filter(u -> u.username().equals(username)).findFirst();
	}

	@Override
	public Optional<AppUser> findById(Long id) {
		return users.stream().filter(u -> id.equals(u.id())).findFirst();
	}

	@Override
	public AppUser add(AppUser user, String passwordHash) {
		AppUser stored = new AppUser(1000L + users.size(), user.username(), user.displayName(), user.roles(),
				user.enabled());
		users.add(stored);
		passwordHashes.put(stored.username(), passwordHash);
		return stored;
	}

	/** The hash add() was given, so a test can check what was stored. */
	public String passwordHashOf(String username) {
		return passwordHashes.get(username);
	}
}
