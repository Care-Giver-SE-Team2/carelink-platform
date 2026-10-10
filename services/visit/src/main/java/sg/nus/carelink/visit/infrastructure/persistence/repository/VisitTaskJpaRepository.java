package sg.nus.carelink.visit.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitTaskJpaEntity;

/** Spring Data repository for visit_task. Used by persistence.adapter only; never exposed outwards. */
public interface VisitTaskJpaRepository extends JpaRepository<VisitTaskJpaEntity, Long> {
	java.util.List<VisitTaskJpaEntity> findByVisitIdOrderByIdAsc(Long visitId);
}
