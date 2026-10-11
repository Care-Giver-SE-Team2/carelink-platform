package sg.nus.carelink.visit.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitAssignmentJpaEntity;

/** Spring Data repository for visit_assignment. Used by persistence.adapter only; never exposed outwards. */
public interface VisitAssignmentJpaRepository extends JpaRepository<VisitAssignmentJpaEntity, Long> {

	Optional<VisitAssignmentJpaEntity> findFirstByVisitIdAndStatusOrderByIdDesc(Long visitId,
			VisitAssignmentJpaEntity.Status status);

	List<VisitAssignmentJpaEntity> findByVisitIdOrderByIdAsc(Long visitId);
}
