package sg.nus.carelink.rostering.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.rostering.infrastructure.persistence.entity.CaregiverPreferenceJpaEntity;

/** Spring Data repository for caregiver_preference. Used by persistence.adapter only; never exposed outwards. */
public interface CaregiverPreferenceJpaRepository extends JpaRepository<CaregiverPreferenceJpaEntity, Long> {
}
