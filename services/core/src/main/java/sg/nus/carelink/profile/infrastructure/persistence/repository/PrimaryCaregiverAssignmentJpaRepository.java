package sg.nus.carelink.profile.infrastructure.persistence.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.profile.infrastructure.persistence.entity.PrimaryCaregiverAssignmentJpaEntity;

/** Spring Data repository for elder_primary_caregiver. Used by persistence.adapter only; never exposed outwards. */
public interface PrimaryCaregiverAssignmentJpaRepository
		extends JpaRepository<PrimaryCaregiverAssignmentJpaEntity, Long> {

	List<PrimaryCaregiverAssignmentJpaEntity> findByElderIdIn(Set<Long> elderIds);
}
