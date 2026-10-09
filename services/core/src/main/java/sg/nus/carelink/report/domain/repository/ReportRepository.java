package sg.nus.carelink.report.domain.repository;

import java.util.Optional;
import java.util.Set;

import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.domain.model.ReportPage;
import sg.nus.carelink.report.domain.model.ReportPeriod;

/**
 * Port for report: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.ReportRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 *
 * <p>Append-only by shape. There is a way to store a new report and a way to store a new
 * correction, and nothing that updates or removes either: a report is archived as generated
 * (UC-MG07 5a), so the port does not offer what the rule forbids.
 */
public interface ReportRepository {

	/** The report with every correction appended to it, oldest first. */
	Optional<Report> findById(Long id);

	/** Tests existence without parsing a draft's possibly absent content. */
	boolean existsById(Long id);

	/** A readable FAMILY report with ordered amendments; applies scope before parsing content. */
	Optional<Report> findFamilyDetail(Long id, Set<Long> readableElderIds);

	/** Latest published/archived FAMILY report for an exact period, by createdAt then id. Omits amendments. */
	Optional<Report> findLatestFamilyFor(Long elderId, ReportPeriod period);

	/** Stores a report that has not been stored before. */
	Report save(Report report);

	/** Stores one correction. The report it belongs to is not written. */
	ReportAmendment saveAmendment(ReportAmendment amendment);

	/**
	 * The report already generated for this elder, reader and period, if there is one.
	 *
	 * <p>What keeps generation idempotent: the weekly run and a manager pressing Generate for
	 * the same week find the same three reports rather than filing a second set.
	 */
	Optional<Report> findFor(Long elderId, Report.Audience audience, ReportPeriod period);

	/**
	 * One page of reports, the most recent period first and the three readers' versions of a
	 * period together, FAMILY, REGULATOR, INTERNAL.
	 *
	 * @param elderId  one elder's reports, or null for every elder's
	 * @param audience one reader's version, or null for all three
	 */
	ReportPage findPage(Long elderId, Report.Audience audience, int page, int size);

	/**
	 * Published or archived FAMILY reports of readable elders, filtered before counting and
	 * paging; newest period start, creation time and id first. List rows omit amendments.
	 */
	ReportPage findFamilyPage(Set<Long> elderIds, int page, int size);
}
