package sg.nus.carelink.profile.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.profile.infrastructure.persistence.entity.CredentialJpaEntity;

/** Spring Data repository for credential. Used by persistence.adapter only; never exposed outwards. */
public interface CredentialJpaRepository extends JpaRepository<CredentialJpaEntity, Long> {
	List<CredentialJpaEntity> findByCaregiverIdOrderByCredentialTypeIdAscIdAsc(Long caregiverId);
}
