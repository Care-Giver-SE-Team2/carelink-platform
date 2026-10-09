package sg.nus.carelink.identity.infrastructure.persistence.adapter;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import sg.nus.carelink.identity.infrastructure.persistence.entity.AppUserJpaEntity;
import sg.nus.carelink.identity.infrastructure.persistence.repository.AppUserJpaRepository;
import sg.nus.carelink.shared.security.Role;

/**
 * Local-only login account, so the temporary "log in as manager" dev shortcut on the
 * landing page has real credentials to authenticate with. Runs only under the "dev"
 * profile (SPRING_PROFILES_ACTIVE=dev, or {@code ./mvnw spring-boot:run
 * -Dspring-boot.run.profiles=dev}) and only when the table is empty, so restarting the
 * app never duplicates rows. Never runs in tests or in a deployed environment.
 */
@Component
@Profile("dev")
class DevUserSeeder implements CommandLineRunner {

	static final String MANAGER_USERNAME = "manager@carelink.sg";
	static final String MANAGER_PASSWORD = "password";

	private final AppUserJpaRepository users;
	private final PasswordEncoder passwordEncoder;

	DevUserSeeder(AppUserJpaRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(String... args) {
		if (users.count() > 0) {
			return;
		}
		AppUserJpaEntity manager = new AppUserJpaEntity();
		manager.setUsername(MANAGER_USERNAME);
		manager.setPasswordHash(passwordEncoder.encode(MANAGER_PASSWORD));
		manager.setDisplayName("Tan Mei Ling");
		manager.setEnabled(true);
		manager.getRoles().add(Role.MANAGER.name());
		users.save(manager);
	}
}
