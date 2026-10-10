package sg.nus.carelink.report.infrastructure.persistence.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.report.infrastructure.persistence.entity.ReportJpaEntity;

/** Spring Data repository for report. Used by persistence.adapter only; never exposed outwards. */
public interface ReportJpaRepository extends JpaRepository<ReportJpaEntity, Long> {

	/** Filters visibility before the adapter deserializes the report or loads its amendments. */
	Optional<ReportJpaEntity> findByIdAndElderIdInAndAudienceAndStatusIn(Long id, Collection<Long> elderIds,
			ReportJpaEntity.Audience audience, Collection<ReportJpaEntity.Status> statuses);

	/**
	 * The report already filed for one elder, reader and period.
	 *
	 * <p>First by id rather than a single result: nothing in the schema makes the four columns
	 * unique, and two runs racing for the same week must still leave this answering, not
	 * throwing on the second row.
	 */
	Optional<ReportJpaEntity> findFirstByElderIdAndAudienceAndPeriodStartAndPeriodEndOrderByIdAsc(
			Long elderId, ReportJpaEntity.Audience audience, LocalDate periodStart, LocalDate periodEnd);

	/** Latest readable source for a family summary; separate from generation's first-id lookup. */
	Optional<ReportJpaEntity> findFirstByElderIdAndAudienceAndStatusInAndPeriodStartAndPeriodEndOrderByCreatedAtDescIdDesc(
			Long elderId, ReportJpaEntity.Audience audience, Collection<ReportJpaEntity.Status> statuses,
			LocalDate periodStart, LocalDate periodEnd);

	/**
	 * One page of every elder's reports for the given readers.
	 *
	 * <p>Readers are a collection rather than a single value so that "any reader" is all three
	 * spelled out: a derived query compares with {@code = null}, which matches no row, and the
	 * unfiltered list would come back empty - the same trap as the incident queue. The order
	 * is the adapter's, on the {@link Pageable}.
	 */
	Page<ReportJpaEntity> findByAudienceIn(Collection<ReportJpaEntity.Audience> audiences, Pageable pageable);

	/** As above, for one elder. */
	Page<ReportJpaEntity> findByElderIdAndAudienceIn(
			Long elderId, Collection<ReportJpaEntity.Audience> audiences, Pageable pageable);

	/** Current family scope, audience and readable states apply to both rows and total. */
	List<ReportJpaEntity> findByElderIdInAndAudienceAndStatusIn(
			Collection<Long> elderIds, ReportJpaEntity.Audience audience,
			Collection<ReportJpaEntity.Status> statuses, Pageable pageable);

	long countByElderIdInAndAudienceAndStatusIn(Collection<Long> elderIds, ReportJpaEntity.Audience audience,
			Collection<ReportJpaEntity.Status> statuses);
}
