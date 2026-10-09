package sg.nus.carelink.report.domain.repository;

import java.util.Collection;
import java.util.Map;

import sg.nus.carelink.report.domain.model.ReportBasis;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportMetrics;

/**
 * Port for the basis a generation run files its reports from. Implemented by
 * infrastructure.persistence.adapter.ReportBasisRepositoryAdapter.
 *
 * <p>Append-only, like the reports: a basis describes what one run read, and nothing that
 * happens afterwards changes what that was.
 */
public interface ReportBasisRepository {

	/**
	 * Stores a basis that has not been stored before, with the facts it was worked out from.
	 *
	 * @param facts the facts {@code basis} was made from; kept beside it for the back office
	 * @return the basis with its id
	 */
	ReportBasis save(ReportBasis basis, ReportFacts facts);

	/** The numbers of each stored basis among the given ids; ids that match nothing are left out. */
	Map<Long, ReportMetrics> findMetrics(Collection<Long> basisIds);
}
