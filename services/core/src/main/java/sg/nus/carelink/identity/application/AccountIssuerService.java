package sg.nus.carelink.identity.application;

import java.security.SecureRandom;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.identity.domain.repository.AppUserRepository;
import sg.nus.carelink.identity.domain.service.UsernameRule;
import sg.nus.carelink.shared.security.Role;

/** Issues accounts for other modules: picks a free username, generates a password, stores only its hash. */
@Service
@Transactional
class AccountIssuerService implements AccountIssuer {

	/** No 0/O, 1/l/I: the password is read off a screen and typed in by someone else. */
	private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
	static final int PASSWORD_LENGTH = 10;
	private static final int MAX_ATTEMPTS = 1000;

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final SecureRandom random = new SecureRandom();

	AccountIssuerService(AppUserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public IssuedAccount issue(String displayName, Role role) {
		String username = freeUsername(UsernameRule.base(displayName));
		String password = temporaryPassword();
		AppUser created = users.add(new AppUser(null, username, displayName, Set.of(role), true),
				passwordEncoder.encode(password));
		return new IssuedAccount(created.id(), created.username(), password);
	}

	private String freeUsername(String base) {
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			String candidate = UsernameRule.candidate(base, attempt);
			if (users.findByUsername(candidate).isEmpty()) {
				return candidate;
			}
		}
		throw new IllegalStateException("No free username for " + base);
	}

	private String temporaryPassword() {
		StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
		for (int i = 0; i < PASSWORD_LENGTH; i++) {
			password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return password.toString();
	}
}
