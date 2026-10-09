package sg.nus.carelink.incident.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.incident.infrastructure.persistence.entity.SpotCheckJpaEntity;

/** Spring Data repository for spot_check. Used by persistence.adapter only; never exposed outwards. */
public interface SpotCheckJpaRepository extends JpaRepository<SpotCheckJpaEntity, Long> {

	List<SpotCheckJpaEntity> findAllByOrderByProposedTimeDescIdDesc();

	List<SpotCheckJpaEntity> findByElderIdInOrderByProposedTimeDescIdDesc(Collection<Long> elderIds);

	List<SpotCheckJpaEntity> findByCaregiverIdOrderByProposedTimeDescIdDesc(Long caregiverId);

	List<SpotCheckJpaEntity> findByApprovalStatusAndOutcomeIsNull(SpotCheckJpaEntity.ApprovalStatus approvalStatus);

	List<SpotCheckJpaEntity> findByOutcomeAndCheckedAtGreaterThanEqual(SpotCheckJpaEntity.Outcome outcome,
			LocalDateTime since);
}
