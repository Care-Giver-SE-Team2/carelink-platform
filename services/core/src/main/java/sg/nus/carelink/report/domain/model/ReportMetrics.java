package sg.nus.carelink.report.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The period's numbers, worked out once from its facts: what a basis stores in columns and what
 * every reader's overview states, so the two can never disagree.
 *
 * @param visitsPlanned      visits of the period that were not cancelled ({@link VisitFact#isPlanned})
 * @param visitsCompleted    of those, the ones carried out ({@link VisitFact#isCompleted})
 * @param fulfilmentRate     completed out of planned, in percent to two places; null when
 *                           nothing was planned, because no visits is not a fulfilment of zero
 * @param vitalsOutOfRange   readings flagged out of range when they were entered
 * @param averageElderRating mean of the elder's ratings of the period's visits, to two places;
 *                           null when the elder rated none
 * @param ratingCount        how many ratings that mean is taken over
 * @param dataComplete       false while a visit of the period is not closed (UC-MG07 2a)
 */
public record ReportMetrics(
		int visitsPlanned,
		int visitsCompleted,
		BigDecimal fulfilmentRate,
		int vitalsOutOfRange,
		int incidentCount,
		BigDecimal averageElderRating,
		int ratingCount,
		boolean dataComplete) {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	/** The numbers of one elder's period. */
	public static ReportMetrics of(ReportFacts facts) {
		int planned = (int) facts.visits().stream().filter(VisitFact::isPlanned).count();
		int completed = (int) facts.visits().stream().filter(VisitFact::isCompleted).count();
		List<Integer> ratings = facts.quality().confirmations().stream()
				.filter(ConfirmationFact::isRated)
				.map(ConfirmationFact::rating)
				.toList();

		return new ReportMetrics(
				planned,
				completed,
				planned == 0 ? null : percent(completed, planned),
				(int) facts.vitals().stream().filter(VitalFact::outOfRange).count(),
				facts.incidents().size(),
				ratings.isEmpty() ? null : mean(ratings),
				ratings.size(),
				facts.unclosedVisits().isEmpty());
	}

	private static BigDecimal percent(int part, int whole) {
		return BigDecimal.valueOf(part).multiply(HUNDRED).divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
	}

	private static BigDecimal mean(List<Integer> values) {
		long sum = values.stream().mapToLong(Integer::longValue).sum();
		return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
	}
}
