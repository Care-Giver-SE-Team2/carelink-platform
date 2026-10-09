package sg.nus.carelink.rostering.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.rostering.infrastructure.persistence.entity.CaregiverAvailabilityJpaEntity;

/** Spring Data repository for caregiver_availability. Used by persistence.adapter only; never exposed outwards. */
public interface CaregiverAvailabilityJpaRepository extends JpaRepository<CaregiverAvailabilityJpaEntity, Long> {
}
