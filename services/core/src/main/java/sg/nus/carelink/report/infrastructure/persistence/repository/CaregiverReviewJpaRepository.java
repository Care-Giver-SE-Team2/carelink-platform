package sg.nus.carelink.report.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.report.infrastructure.persistence.entity.CaregiverReviewJpaEntity;

/** Spring Data repository for caregiver_review. Used by persistence.adapter only; never exposed outwards. */
public interface CaregiverReviewJpaRepository extends JpaRepository<CaregiverReviewJpaEntity, Long> {
}
