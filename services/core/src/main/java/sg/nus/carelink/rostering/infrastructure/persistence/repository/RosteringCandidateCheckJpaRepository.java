package sg.nus.carelink.rostering.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringCandidateCheckJpaEntity;

/** Spring Data repository for rostering_candidate_check. Used by persistence.adapter only; never exposed outwards. */
public interface RosteringCandidateCheckJpaRepository extends JpaRepository<RosteringCandidateCheckJpaEntity, Long> {

	List<RosteringCandidateCheckJpaEntity> findByRosteringCandidateIdInOrderByIdAsc(Collection<Long> rosteringCandidateIds);
}
