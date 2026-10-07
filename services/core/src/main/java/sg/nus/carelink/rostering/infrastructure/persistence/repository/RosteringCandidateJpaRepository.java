package sg.nus.carelink.rostering.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringCandidateJpaEntity;

/** Spring Data repository for rostering_candidate. Used by persistence.adapter only; never exposed outwards. */
public interface RosteringCandidateJpaRepository extends JpaRepository<RosteringCandidateJpaEntity, Long> {

	List<RosteringCandidateJpaEntity> findByRosteringRunIdAndVisitIdOrderByIdAsc(Long rosteringRunId, Long visitId);
}
