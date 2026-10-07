package sg.nus.carelink.identity.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.shared.security.Role;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reference example of an application-layer unit test: the database is replaced by a fake
 * repository and Spring is never started.
 */
class UserDirectoryServiceTest {

	private final AppUser alice = new AppUser(1L, "alice", "Alice Tan", Set.of(Role.MANAGER), true);
	private final UserDirectory directory =
			new UserDirectoryService(new InMemoryAppUserRepository().with(alice));

	@Test
	@DisplayName("looks an account up by username")
	void findsByUsername() {
		assertThat(directory.findByUsername("alice")).contains(alice);
	}

	@Test
	@DisplayName("looks an account up by id")
	void findsById() {
		assertThat(directory.findById(1L)).contains(alice);
	}

	@Test
	@DisplayName("returns empty rather than throwing when nothing matches")
	void returnsEmptyWhenAbsent() {
		assertThat(directory.findByUsername("nobody")).isEmpty();
		assertThat(directory.findById(999L)).isEmpty();
	}
}
