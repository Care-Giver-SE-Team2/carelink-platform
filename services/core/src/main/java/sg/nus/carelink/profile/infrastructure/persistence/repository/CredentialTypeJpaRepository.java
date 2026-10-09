package sg.nus.carelink.profile.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.profile.infrastructure.persistence.entity.CredentialTypeJpaEntity;

/** Spring Data repository for credential_type. Used by persistence.adapter only; never exposed outwards. */
public interface CredentialTypeJpaRepository extends JpaRepository<CredentialTypeJpaEntity, Long> {
}
