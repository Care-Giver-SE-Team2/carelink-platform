package sg.nus.carelink.rostering.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * Strategy: how the candidates who passed every hard rule are ranked. The manager picks one per
 * run - the objective tabs of the roster screen - and rostering_run.objective records which ran.
 *
 * <p>The rules decide who may go; the objective decides who should. Keeping the two apart is
 * what lets an institution that cares most about continuity and one that cares most about
 * spreading the load use the same rules: a new objective is one class, and nothing in
 * {@link ReplacementFinder} or the rules changes.
 */
public interface ScoringObjective {

	RosteringRun.Objective objective();

	/** Higher is better, between 0 and 100. */
	BigDecimal score(CandidateContext candidate, Map<String, RuleCheck> checks);

	/** The one line the manager and the family are shown next to the name. */
	String reason(CandidateContext candidate, Map<String, RuleCheck> checks);

	/**
	 * The objective that implements this choice. COST has a column but no data behind it -
	 * CareLink keeps no pay rates - so asking for it is refused rather than quietly ranked by
	 * something else.
	 */
	static ScoringObjective of(RosteringRun.Objective objective) {
		return switch (objective == null ? RosteringRun.Objective.CONTINUITY : objective) {
			case CONTINUITY -> new ContinuityFirst();
			case EVEN_WORKLOAD -> new EvenWorkload();
			case TRAVEL_TIME -> new ShortestTravel();
			case COST -> throw new BusinessRuleViolation("OBJECTIVE_NOT_SUPPORTED",
					"Ranking by cost needs pay rates, which CareLink does not keep");
		};
	}

	/** Clamps to [0, 100] at two decimals, the shape of rostering_candidate.score. */
	static BigDecimal bounded(int raw) {
		return BigDecimal.valueOf(Math.max(0, Math.min(100, raw))).setScale(2, RoundingMode.HALF_UP);
	}

	/**
	 * The soft signals every objective weighs, read from the checks so that a rule switched
	 * off in the table stops counting too.
	 */
	record Signals(int priorVisits, boolean dialect, boolean sector, int visitsThatDay, boolean concern) {

		/** Visits to the same elder stop adding after this many; the fifth and the fiftieth are alike. */
		static final int PRIOR_VISITS_CAP = 5;

		static Signals of(CandidateContext candidate, Map<String, RuleCheck> checks) {
			RuleCheck continuity = checks.get(ReplacementRules.CONTINUITY);
			int prior = continuity != null && continuity.passed()
					? Math.min(candidate.priorVisits(), PRIOR_VISITS_CAP)
					: 0;
			return new Signals(prior,
					passed(checks, ReplacementRules.DIALECT_MATCH),
					passed(checks, ReplacementRules.SECTOR_BAND),
					candidate.visitsThatDay(),
					failed(checks, ReplacementRules.SPOT_CHECK));
		}

		private static boolean passed(Map<String, RuleCheck> checks, String code) {
			RuleCheck check = checks.get(code);
			return check != null && check.passed();
		}

		private static boolean failed(Map<String, RuleCheck> checks, String code) {
			RuleCheck check = checks.get(code);
			return check != null && check.failed();
		}

		int flag(boolean value) {
			return value ? 1 : 0;
		}
	}
}
