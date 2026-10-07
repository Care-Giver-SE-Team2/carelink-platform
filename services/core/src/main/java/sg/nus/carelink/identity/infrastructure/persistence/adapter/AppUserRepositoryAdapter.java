package sg.nus.carelink.identity.infrastructure.persistence.adapter;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.identity.domain.repository.AppUserRepository;
import sg.nus.carelink.identity.infrastructure.persistence.entity.AppUserJpaEntity;
import sg.nus.carelink.identity.infrastructure.persistence.repository.AppUserJpaRepository;

import java.util.Optional;

/**
 * Implementation of the domain repository interface. The dependency points
 * infrastructure -> domain, not the other way round. This is what dependency
 * inversion looks like in code.
 */
@Repository
class AppUserRepositoryAdapter implements AppUserRepository {

	private final AppUserJpaRepository jpa;

	AppUserRepositoryAdapter(AppUserJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<AppUser> findByUsername(String username) {
		return jpa.findByUsername(username).map(AppUserMapper::toDomain);
	}

	@Override
	public Optional<AppUser> findById(Long id) {
		return jpa.findById(id).map(AppUserMapper::toDomain);
	}

	@Override
	public AppUser add(AppUser user, String passwordHash) {
		AppUserJpaEntity entity = new AppUserJpaEntity();
		entity.setUsername(user.username());
		entity.setPasswordHash(passwordHash);
		entity.setDisplayName(user.displayName());
		entity.setEnabled(user.enabled());
		user.roles().forEach(role -> entity.getRoles().add(role.name()));
		return AppUserMapper.toDomain(jpa.save(entity));
	}
}
