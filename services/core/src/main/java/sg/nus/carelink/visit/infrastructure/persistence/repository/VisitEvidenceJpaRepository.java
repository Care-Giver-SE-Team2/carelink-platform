package sg.nus.carelink.visit.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitEvidenceJpaEntity;

/** Spring Data repository for visit_evidence. Used by persistence.adapter only; never exposed outwards. */
public interface VisitEvidenceJpaRepository extends JpaRepository<VisitEvidenceJpaEntity, Long> {
}
