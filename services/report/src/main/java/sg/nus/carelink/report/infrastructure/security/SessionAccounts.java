package sg.nus.carelink.report.infrastructure.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import sg.nus.carelink.platform.security.SignedInUser;
import sg.nus.carelink.platform.security.SignedInUsers;
import sg.nus.carelink.report.application.Accounts;

/**
 * {@link Accounts} from the shared session: core wrote the account id into it at sign-in, so no
 * call to core is needed.
 */
@Component
class SessionAccounts implements Accounts {

	private final SignedInUsers signedIn;

	SessionAccounts(SignedInUsers signedIn) {
		this.signedIn = signedIn;
	}

	@Override
	public Long idOf(String username) {
		SignedInUser user = signedIn.require();
		if (!user.username().equals(username)) {
			throw new AccessDeniedException("Only the signed-in account can be looked up");
		}
		return user.id();
	}

}
