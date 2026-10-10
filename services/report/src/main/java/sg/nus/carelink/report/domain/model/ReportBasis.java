package sg.nus.carelink.report.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * The basis of one generation run ("底稿", UC-MG07 step 2): the elder and period it covers and
 * the numbers worked out from its facts. The three readers' reports of the run are assembled
 * from the same facts and refer to it.
 *
 * <p>The facts themselves are stored beside it, as a document for the back office, by the
 * repository that stores the basis; this record carries only what is read back.
 *
 * @param id null until it has been stored
 */
public record ReportBasis(Long id, Long elderId, ReportPeriod period, ReportMetrics metrics, LocalDateTime createdAt) {

	public ReportBasis {
		Objects.requireNonNull(elderId, "elderId");
		Objects.requireNonNull(period, "period");
		Objects.requireNonNull(metrics, "metrics");
		Objects.requireNonNull(createdAt, "createdAt");
	}

	/**
	 * The basis of the given facts, not yet stored.
	 *
	 * @param now the application clock's reading, the same the run's reports are dated with
	 */
	public static ReportBasis of(ReportFacts facts, LocalDateTime now) {
		return new ReportBasis(null, facts.elderId(), facts.period(), ReportMetrics.of(facts), now);
	}
}
