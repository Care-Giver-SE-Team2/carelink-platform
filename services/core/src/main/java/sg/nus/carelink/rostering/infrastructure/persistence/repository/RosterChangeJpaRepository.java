package sg.nus.carelink.rostering.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosterChangeJpaEntity;

/** Spring Data repository for roster_change. Used by persistence.adapter only; never exposed outwards. */
public interface RosterChangeJpaRepository extends JpaRepository<RosterChangeJpaEntity, Long> {

	/** SELECT ... FOR UPDATE: the family's answer and the default plan take turns. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from RosterChangeJpaEntity c where c.id = :id")
	Optional<RosterChangeJpaEntity> lockById(@Param("id") Long id);

	List<RosterChangeJpaEntity> findByAbsenceIdOrderByVisitStartAscIdAsc(Long absenceId);

	List<RosterChangeJpaEntity> findByElderIdInOrderByVisitStartAscIdAsc(Collection<Long> elderIds);

	List<RosterChangeJpaEntity> findByStatusAndRespondByLessThanEqualOrderByRespondByAsc(
			RosterChangeJpaEntity.Status status, LocalDateTime respondBy);
}
