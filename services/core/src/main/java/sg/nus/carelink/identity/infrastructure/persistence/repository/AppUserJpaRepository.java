package sg.nus.carelink.identity.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.identity.infrastructure.persistence.entity.AppUserJpaEntity;

import java.util.Optional;

/** Spring Data repository. Used inside the persistence layer only; never exposed outwards. */
public interface AppUserJpaRepository extends JpaRepository<AppUserJpaEntity, Long> {

	Optional<AppUserJpaEntity> findByUsername(String username);
}
