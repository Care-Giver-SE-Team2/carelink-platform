package sg.nus.carelink.visit.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitStateTransitionJpaEntity;

/** Spring Data repository for visit_state_transition. Used by persistence.adapter only; never exposed outwards. */
public interface VisitStateTransitionJpaRepository extends JpaRepository<VisitStateTransitionJpaEntity, Long> {

	List<VisitStateTransitionJpaEntity> findByVisitIdAndResultOrderByOccurredAtAscIdAsc(
			Long visitId, VisitStateTransitionJpaEntity.Result result);
}
