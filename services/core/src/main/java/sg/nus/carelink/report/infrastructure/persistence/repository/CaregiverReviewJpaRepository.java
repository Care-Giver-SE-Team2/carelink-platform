package sg.nus.carelink.report.infrastructure.persistence.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.report.infrastructure.persistence.entity.CaregiverReviewJpaEntity;

/** Spring Data repository for caregiver_review. */
public interface CaregiverReviewJpaRepository extends JpaRepository<CaregiverReviewJpaEntity, Long> {

    List<CaregiverReviewJpaEntity> findByElderIdOrderByCreatedAtDescIdDesc(Long elderId);

    boolean existsByFamilyMemberIdAndElderIdAndCaregiverIdAndPeriodStartAndPeriodEnd(
            Long familyMemberId,
            Long elderId,
            Long caregiverId,
            LocalDate periodStart,
            LocalDate periodEnd);
}
