package sg.nus.carelink.rostering.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringCandidateJpaEntity;

/** Spring Data repository for rostering_candidate. Used by persistence.adapter only; never exposed outwards. */
public interface RosteringCandidateJpaRepository extends JpaRepository<RosteringCandidateJpaEntity, Long> {

	List<RosteringCandidateJpaEntity> findByRosteringRunIdAndVisitIdOrderByIdAsc(Long rosteringRunId, Long visitId);

	@Modifying(flushAutomatically = true)
	@Query("update RosteringCandidateJpaEntity c set c.visitId = :toVisitId"
			+ " where c.rosteringRunId = :runId and c.visitId = :fromVisitId")
	int moveToVisit(@Param("runId") Long runId, @Param("fromVisitId") Long fromVisitId,
			@Param("toVisitId") Long toVisitId);
}
