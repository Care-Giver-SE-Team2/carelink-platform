package sg.nus.carelink.identity.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import sg.nus.carelink.identity.infrastructure.persistence.entity.AppUserJpaEntity;
import sg.nus.carelink.identity.infrastructure.persistence.repository.AppUserJpaRepository;
import sg.nus.carelink.shared.security.Role;

/** Runs only under the "dev" profile; here it is exercised directly, bypassing @Profile. */
class DevUserSeederTest {

	private final AppUserJpaRepository users = mock(AppUserJpaRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final DevUserSeeder seeder = new DevUserSeeder(users, passwordEncoder);

	@Test
	void seedsTheManagerAccountWhenTheTableIsEmpty() {
		when(users.count()).thenReturn(0L);
		when(passwordEncoder.encode(DevUserSeeder.MANAGER_PASSWORD)).thenReturn("hashed");

		seeder.run();

		verify(users).save(any(AppUserJpaEntity.class));
	}

	@Test
	void savedManagerHasTheExpectedUsernameAndRole() {
		when(users.count()).thenReturn(0L);
		when(passwordEncoder.encode(DevUserSeeder.MANAGER_PASSWORD)).thenReturn("hashed");

		seeder.run();

		var captor = org.mockito.ArgumentCaptor.forClass(AppUserJpaEntity.class);
		verify(users).save(captor.capture());
		AppUserJpaEntity saved = captor.getValue();
		assertThat(saved.getUsername()).isEqualTo(DevUserSeeder.MANAGER_USERNAME);
		assertThat(saved.getPasswordHash()).isEqualTo("hashed");
		assertThat(saved.isEnabled()).isTrue();
		assertThat(saved.getRoles()).containsExactly(Role.MANAGER.name());
	}

	@Test
	void doesNothingWhenTheTableAlreadyHasRows() {
		when(users.count()).thenReturn(1L);

		seeder.run();

		verify(users, never()).save(any());
	}
}
