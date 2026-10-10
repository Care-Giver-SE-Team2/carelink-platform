package sg.nus.carelink.report.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import sg.nus.carelink.platform.security.SignedInUser;
import sg.nus.carelink.platform.security.SignedInUsers;

/** The account behind a request is the one in the shared session, and no other. */
class SessionAccountsTest {

	private final SignedInUsers signedIn = mock(SignedInUsers.class);

	private final SessionAccounts accounts = new SessionAccounts(signedIn);

	@Test
	void theSignedInAccountsIdComesFromTheSession() {
		when(signedIn.require()).thenReturn(new SignedInUser(10L, "manager", "Manager", Set.of("MANAGER")));

		assertThat(accounts.idOf("manager")).isEqualTo(10L);
	}

	@Test
	void noOtherAccountCanBeLookedUp() {
		when(signedIn.require()).thenReturn(new SignedInUser(10L, "manager", "Manager", Set.of("MANAGER")));

		assertThatThrownBy(() -> accounts.idOf("family-a")).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void nobodySignedInIsUnauthorized() {
		when(signedIn.require()).thenThrow(new AuthenticationCredentialsNotFoundException("Nobody is signed in"));

		assertThatThrownBy(() -> accounts.idOf("manager")).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
	}

}
