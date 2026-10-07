package sg.nus.carelink.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.shared.security.Role;

/** Issuing a login for another module: a free username, one role, and only the password's hash stored. */
class AccountIssuerServiceTest {

	private final InMemoryAppUserRepository users = new InMemoryAppUserRepository();
	private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
	private final AccountIssuerService issuer = new AccountIssuerService(users, encoder);

	@Test
	void issuesAnEnabledAccountWithTheRoleAndAHashedTemporaryPassword() {
		AccountIssuer.IssuedAccount issued = issuer.issue("Tan Bee Choo", Role.ELDER);

		assertThat(issued.username()).isEqualTo("tan.bee.choo");
		assertThat(issued.temporaryPassword()).hasSize(AccountIssuerService.PASSWORD_LENGTH)
				.doesNotContainAnyWhitespaces().matches("[A-HJ-NP-Za-km-z2-9]+");
		AppUser stored = users.findById(issued.userId()).orElseThrow();
		assertThat(stored.displayName()).isEqualTo("Tan Bee Choo");
		assertThat(stored.roles()).containsExactly(Role.ELDER);
		assertThat(stored.enabled()).isTrue();
		String hash = users.passwordHashOf("tan.bee.choo");
		assertThat(hash).isNotEqualTo(issued.temporaryPassword());
		assertThat(encoder.matches(issued.temporaryPassword(), hash)).isTrue();
	}

	@Test
	void aTakenUsernameGetsTheNextNumber() {
		users.with(new AppUser(1L, "tan.bee.choo", "Someone", Set.of(Role.FAMILY), true))
				.with(new AppUser(2L, "tan.bee.choo2", "Someone else", Set.of(Role.ELDER), true));

		assertThat(issuer.issue("Tan Bee Choo", Role.ELDER).username()).isEqualTo("tan.bee.choo3");
	}

	@Test
	void eachAccountGetsADifferentPassword() {
		assertThat(issuer.issue("A", Role.ELDER).temporaryPassword())
				.isNotEqualTo(issuer.issue("B", Role.ELDER).temporaryPassword());
	}
}
