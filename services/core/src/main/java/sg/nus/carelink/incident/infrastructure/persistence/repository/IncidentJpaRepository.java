package sg.nus.carelink.incident.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentJpaEntity;

/** Spring Data repository for incident. Used by persistence.adapter only; never exposed outwards. */
public interface IncidentJpaRepository extends JpaRepository<IncidentJpaEntity, Long> {

	/**
	 * Backs the scheduled scan of UC-SYS02: incidents in one of the given states whose
	 * response deadline has passed, oldest deadline first.
	 *
	 * <p>Uses the idx_incident_deadline (status, respond_by) index created for it in V2.
	 * Which states count as "still waiting to be taken over" is a domain rule and is
	 * supplied by the adapter, not decided here.
	 */
	List<IncidentJpaEntity> findByStatusInAndRespondByNotNullAndRespondByLessThanEqualOrderByRespondByAsc(
			Collection<IncidentJpaEntity.Status> statuses, LocalDateTime deadline);

	List<IncidentJpaEntity> findByElderIdOrderByReportedAtDesc(Long elderId);

	/**
	 * Recent incidents for one elder that had a responder and are not the one being routed.
	 *
	 * <p>The adapter takes the first row as the manager who knows this elder. Limiting the
	 * page keeps it to one index read; the caller only ever wants the newest.
	 */
	List<IncidentJpaEntity> findByElderIdAndIdNotAndResponderUserIdNotNullOrderByReportedAtDesc(
			Long elderId, Long excludedId, Pageable pageable);

	/**
	 * One page of the manager's queue, most urgent first.
	 *
	 * <p>The order is written into the query rather than passed as a {@code Sort}, because
	 * it is not an order over columns. Three tiers, then the deadline inside each:
	 * <ol>
	 *   <li>incidents the chain ran out on ({@code pinned}). UC-MG05 3b pins them to the top
	 *       of the view: nobody is answerable for them any more;</li>
	 *   <li>incidents with nobody named on them. An SOS that was never routed has no
	 *       countdown, so the scheduled scan will never move it - only a person reading this
	 *       list will;</li>
	 *   <li>everything else.</li>
	 * </ol>
	 * Inside a tier the nearest respond_by comes first and the ones with none come after,
	 * newest first among those. The nulls are placed with a CASE because MySQL sorts nulls
	 * first in ascending order and has no NULLS LAST of its own.
	 *
	 * <p>{@code elderId} null means every elder. Severity is a collection, never null: a null
	 * enum parameter compares {@code = null} and matches no row, so "any severity" is passed
	 * as all three. The page request must be unsorted, or its sort is appended after this one.
	 */
	@Query(value = """
			select i from IncidentJpaEntity i
			where i.status in :statuses
			  and i.severity in :severities
			  and (:elderId is null or i.elderId = :elderId)
			order by
			  case when i.status = :pinned then 0
			       when i.responderUserId is null then 1
			       else 2 end,
			  case when i.respondBy is null then 1 else 0 end,
			  i.respondBy asc,
			  i.reportedAt desc
			""",
			countQuery = """
			select count(i) from IncidentJpaEntity i
			where i.status in :statuses
			  and i.severity in :severities
			  and (:elderId is null or i.elderId = :elderId)
			""")
	Page<IncidentJpaEntity> findQueue(
			@Param("statuses") Collection<IncidentJpaEntity.Status> statuses,
			@Param("severities") Collection<IncidentJpaEntity.Severity> severities,
			@Param("elderId") Long elderId,
			@Param("pinned") IncidentJpaEntity.Status pinned,
			Pageable pageable);
}
