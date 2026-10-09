package sg.nus.carelink.identity.infrastructure.persistence.adapter;

import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.identity.infrastructure.persistence.entity.AppUserJpaEntity;
import sg.nus.carelink.shared.security.Role;

import java.util.Set;
import java.util.stream.Collectors;

/** JPA entity to domain model. One direction only: the domain model knows nothing of JPA. */
final class AppUserMapper {

	private AppUserMapper() {
	}

	static AppUser toDomain(AppUserJpaEntity entity) {
		Set<Role> roles = entity.getRoles().stream()
				.map(Role::valueOf)
				.collect(Collectors.toUnmodifiableSet());
		return new AppUser(entity.getId(), entity.getUsername(), entity.getDisplayName(), roles, entity.isEnabled());
	}
}
