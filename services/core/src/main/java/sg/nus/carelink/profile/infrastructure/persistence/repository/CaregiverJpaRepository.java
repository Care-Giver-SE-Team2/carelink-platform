package sg.nus.carelink.profile.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.profile.infrastructure.persistence.entity.CaregiverJpaEntity;

/** Spring Data repository for caregiver. Used by persistence.adapter only; never exposed outwards. */
public interface CaregiverJpaRepository extends JpaRepository<CaregiverJpaEntity, Long> {
	java.util.Optional<CaregiverJpaEntity> findByUserId(Long userId);

	java.util.List<CaregiverJpaEntity> findAllByOrderByFullNameAsc();

	java.util.List<CaregiverJpaEntity> findByIdIn(java.util.Set<Long> ids);
}
