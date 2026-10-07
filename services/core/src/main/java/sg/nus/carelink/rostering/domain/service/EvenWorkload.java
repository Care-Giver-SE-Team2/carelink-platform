package sg.nus.carelink.rostering.domain.service;

import java.math.BigDecimal;
import java.util.Map;

import sg.nus.carelink.rostering.domain.model.RosteringRun;

/**
 * "Even workload": whoever has the lightest day comes first, so one absence does not turn
 * into one colleague's twelve-hour day.
 */
final class EvenWorkload implements ScoringObjective {

	@Override
	public RosteringRun.Objective objective() {
		return RosteringRun.Objective.EVEN_WORKLOAD;
	}

	@Override
	public BigDecimal score(CandidateContext candidate, Map<String, RuleCheck> checks) {
		Signals s = Signals.of(candidate, checks);
		return ScoringObjective.bounded(50 - 8 * s.visitsThatDay() + 2 * s.priorVisits() + 2 * s.flag(s.dialect())
				+ 2 * s.flag(s.sector()) - 10 * s.flag(s.concern()));
	}

	@Override
	public String reason(CandidateContext candidate, Map<String, RuleCheck> checks) {
		int others = candidate.visitsThatDay();
		return others == 0
				? "Has no other visit that day"
				: "Lighter day: %d other visit%s".formatted(others, others == 1 ? "" : "s");
	}
}
