package sg.nus.carelink.profile.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.identity.application.AccountIssuer;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.repository.FamilyMemberRepository;
import sg.nus.carelink.shared.security.Role;

/**
 * A family member signing up from the landing page, before they have any role: creates their
 * FAMILY login and family profile in one transaction. Applying for care for an elder is the next
 * step, made signed in, through FamilyServiceApplicationService.
 */
@Service
public class FamilyRegistrationService {

	private final AccountIssuer accounts;
	private final FamilyMemberRepository families;

	public FamilyRegistrationService(AccountIssuer accounts, FamilyMemberRepository families) {
		this.accounts = accounts;
		this.families = families;
	}

	/**
	 * @return The new family profile, linked to the new account
	 * @throws sg.nus.carelink.shared.error.BusinessRuleViolation If the username is already taken
	 */
	@Transactional
	public FamilyMember register(String username, String rawPassword, String fullName, String phone) {
		Long userId = accounts.register(username, fullName, rawPassword, Role.FAMILY);
		return families.save(new FamilyMember(null, userId, fullName, phone, null, null, null));
	}
}
