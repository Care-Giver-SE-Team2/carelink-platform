package sg.nus.carelink.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.identity.application.AccountIssuer;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.security.Role;

/** Signing up as a family member: a FAMILY login and the family profile linked to it. */
class FamilyRegistrationServiceTest {

	private final InMemoryFamilyMemberRepository families = new InMemoryFamilyMemberRepository();
	private final List<String> registered = new ArrayList<>();
	private final AccountIssuer accounts = new AccountIssuer() {
		@Override
		public IssuedAccount issue(String displayName, Role role) {
			throw new UnsupportedOperationException("sign-up never issues a temporary password");
		}

		@Override
		public Long register(String username, String displayName, String rawPassword, Role role) {
			if (username.equals("taken")) {
				throw new BusinessRuleViolation("USERNAME_TAKEN", "That username is already taken");
			}
			registered.add(username + "/" + displayName + "/" + rawPassword + "/" + role);
			return 700L;
		}
	};
	private final FamilyRegistrationService service = new FamilyRegistrationService(accounts, families);

	@Test
	void createsAFamilyLoginAndAProfileLinkedToIt() {
		FamilyMember family = service.register("lim.family", "chosen-password", "Lim Wei Ling", "+65 9123 4567");

		assertThat(registered).containsExactly("lim.family/Lim Wei Ling/chosen-password/FAMILY");
		assertThat(family.userId()).isEqualTo(700L);
		assertThat(family.fullName()).isEqualTo("Lim Wei Ling");
		assertThat(family.phone()).isEqualTo("+65 9123 4567");
		assertThat(families.findByUserId(700L)).contains(family);
	}

	@Test
	void aTakenUsernameCreatesNoProfile() {
		assertThatThrownBy(() -> service.register("taken", "chosen-password", "Lim Wei Ling", "91234567"))
				.isInstanceOf(BusinessRuleViolation.class);
		assertThat(families.findByUserId(700L)).isEmpty();
	}
}
