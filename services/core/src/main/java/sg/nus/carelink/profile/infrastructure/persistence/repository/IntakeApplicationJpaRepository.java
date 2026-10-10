package sg.nus.carelink.profile.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sg.nus.carelink.profile.infrastructure.persistence.entity.IntakeApplicationJpaEntity;

/** Spring Data repository for intake_application. Used by persistence.adapter only; never exposed outwards. */
public interface IntakeApplicationJpaRepository extends JpaRepository<IntakeApplicationJpaEntity, Long> {

	@Query("""
			SELECT application FROM IntakeApplicationJpaEntity application
			WHERE application.applicantFamilyMemberId = :familyMemberId
			AND (:status IS NULL OR application.status = :status)
			""")
	List<IntakeApplicationJpaEntity> findForApplicant(@Param("familyMemberId") Long familyMemberId,
			@Param("status") IntakeApplicationJpaEntity.Status status, Pageable pageable);

	@Query("""
			SELECT COUNT(application) FROM IntakeApplicationJpaEntity application
			WHERE application.applicantFamilyMemberId = :familyMemberId
			AND (:status IS NULL OR application.status = :status)
			""")
	long countForApplicant(@Param("familyMemberId") Long familyMemberId,
			@Param("status") IntakeApplicationJpaEntity.Status status);

	List<IntakeApplicationJpaEntity> findByElderIdAndStatus(Long elderId, IntakeApplicationJpaEntity.Status status);
}
