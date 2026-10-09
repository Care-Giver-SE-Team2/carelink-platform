package sg.nus.carelink.report.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportPeriod;

/**
 * Port for the records a report is made from: visits, readings, the caregivers' notes and
 * incidents. None of them belong to the report module.
 *
 * <p>An interface so that the application layer can be tested with facts built by hand, and
 * so that where the facts come from - today, plain reads of the visit and incident tables -
 * can change without anything above this line noticing.
 */
public interface ReportFactsSource {

	/**
	 * Everything the elder's period produced.
	 *
	 * @return empty when there is no such elder; a real elder with a quiet week gets facts
	 *         with nothing in them, which is a report worth filing
	 */
	Optional<ReportFacts> gather(Long elderId, ReportPeriod period);

	/** The elders who had at least one visit scheduled in the period: who the weekly run reports on. */
	List<Long> eldersWithVisitsIn(ReportPeriod period);
}
