package sg.nus.carelink.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class SignedInUsersTest {

	private final SignedInUsers users = new SignedInUsers();

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void readsTheUserFromTheSecurityContextAndTheSession() {
		signIn("ana", "ROLE_FAMILY", "ROLE_ELDER");
		MockHttpServletRequest request = request();
		SignedInUserSession.remember(request.getSession(), 42L, "Ana Tan");

		assertThat(users.current()).contains(new SignedInUser(42L, "ana", "Ana Tan", Set.of("FAMILY", "ELDER")));
		assertThat(users.require().hasRole("FAMILY")).isTrue();
	}

	@Test
	void anAnonymousRequestHasNoUser() {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
				"key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
		request();

		assertThat(users.current()).isEmpty();
		assertThatThrownBy(users::require).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
	}

	@Test
	void aSessionWithoutTheAccountIdIsNotEnoughToRequireAUser() {
		signIn("ana", "ROLE_FAMILY");
		request();

		assertThat(users.current()).hasValueSatisfying(user -> assertThat(user.id()).isNull());
		assertThatThrownBy(users::require).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
	}

	private static void signIn(String username, String... authorities) {
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				username, null, java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
	}

	private static MockHttpServletRequest request() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
		return request;
	}

}
