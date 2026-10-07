package sg.nus.carelink.identity.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.identity.domain.repository.AppUserRepository;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
class UserDirectoryService implements UserDirectory {

	private final AppUserRepository users;

	UserDirectoryService(AppUserRepository users) {
		this.users = users;
	}

	@Override
	public Optional<AppUser> findByUsername(String username) {
		return users.findByUsername(username);
	}

	@Override
	public Optional<AppUser> findById(Long id) {
		return users.findById(id);
	}
}
