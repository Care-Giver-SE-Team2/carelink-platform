package sg.nus.carelink.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;

import sg.nus.carelink.identity.application.IdentityService;
import sg.nus.carelink.identity.controller.dto.CurrentUserResponse;
import sg.nus.carelink.identity.controller.dto.LoginRequest;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.shared.security.Role;

class AuthControllerTest {

	private final IdentityService identityService =
			mock(IdentityService.class);

	private final AuthController controller =
			new AuthController(identityService);

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void resolvesCsrfToken() {
		CsrfToken csrfToken =
				mock(CsrfToken.class);

		when(csrfToken.getToken())
				.thenReturn("test-csrf-token");

		controller.csrf(csrfToken);

		verify(csrfToken)
				.getToken();
	}

	@Test
	void logsInAndStoresAuthenticationInSession() {
		LoginRequest loginRequest =
				new LoginRequest(
						"elder_test",
						"Elder123!"
				);

		Authentication authentication =
				UsernamePasswordAuthenticationToken.authenticated(
						"elder_test",
						null,
						java.util.List.of()
				);

		AppUser elder = new AppUser(
				1L,
				"elder_test",
				"Test Elder",
				Set.of(Role.ELDER),
				true
		);

		when(identityService.authenticate(
				"elder_test",
				"Elder123!"
		)).thenReturn(authentication);

		when(identityService.require("elder_test"))
				.thenReturn(elder);

		MockHttpServletRequest request =
				new MockHttpServletRequest();

		MockHttpServletResponse response =
				new MockHttpServletResponse();

		CurrentUserResponse result =
				controller.login(
						loginRequest,
						request,
						response
				);

		assertThat(result.id())
				.isEqualTo(1L);

		assertThat(result.username())
				.isEqualTo("elder_test");

		assertThat(result.displayName())
				.isEqualTo("Test Elder");

		assertThat(result.roles())
				.containsExactly("ELDER");

		assertThat(
				SecurityContextHolder
						.getContext()
						.getAuthentication()
		).isSameAs(authentication);

		assertThat(request.getSession(false))
				.isNotNull();

		verify(identityService)
				.authenticate(
						"elder_test",
						"Elder123!"
				);

		verify(identityService)
				.require("elder_test");
	}

	@Test
	void returnsCurrentAuthenticatedUser() {
		Authentication authentication =
				UsernamePasswordAuthenticationToken.authenticated(
						"elder_test",
						null,
						java.util.List.of()
				);

		AppUser elder = new AppUser(
				1L,
				"elder_test",
				"Test Elder",
				Set.of(Role.ELDER),
				true
		);

		when(identityService.require("elder_test"))
				.thenReturn(elder);

		CurrentUserResponse result =
				controller.me(authentication);

		assertThat(result.id())
				.isEqualTo(1L);

		assertThat(result.username())
				.isEqualTo("elder_test");

		assertThat(result.displayName())
				.isEqualTo("Test Elder");

		assertThat(result.roles())
				.containsExactly("ELDER");

		verify(identityService)
				.require("elder_test");
	}

	@Test
	void badCredentialsHandlerDoesNotThrow() {
		assertThatCode(controller::onBadCredentials)
				.doesNotThrowAnyException();
	}
}