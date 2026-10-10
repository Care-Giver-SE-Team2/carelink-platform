package sg.nus.carelink.report.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.report.infrastructure.persistence.entity.ReportAmendmentJpaEntity;

/** Spring Data repository for report_amendment. Used by persistence.adapter only; never exposed outwards. */
public interface ReportAmendmentJpaRepository extends JpaRepository<ReportAmendmentJpaEntity, Long> {

	/**
	 * One report's corrections, oldest first. Ordered by id as well as by time because two
	 * corrections can share a timestamp, and a list of corrections that reorders itself between
	 * two reads is not a record.
	 */
	List<ReportAmendmentJpaEntity> findByReportIdOrderByCreatedAtAscIdAsc(Long reportId);

	/** The corrections of every report on one page of the list, in one query rather than one per row. */
	List<ReportAmendmentJpaEntity> findByReportIdInOrderByCreatedAtAscIdAsc(Collection<Long> reportIds);
}
