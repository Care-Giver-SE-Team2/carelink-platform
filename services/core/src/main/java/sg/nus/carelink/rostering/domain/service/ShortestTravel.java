package sg.nus.carelink.rostering.domain.service;

import java.math.BigDecimal;
import java.util.Map;

import sg.nus.carelink.rostering.domain.model.RosteringRun;

/**
 * "Travel time": somebody already working in the elder's sector comes first. CareLink keeps
 * sectors rather than coordinates, so the same sector is the honest stand-in for a short trip.
 */
final class ShortestTravel implements ScoringObjective {

	@Override
	public RosteringRun.Objective objective() {
		return RosteringRun.Objective.TRAVEL_TIME;
	}

	@Override
	public BigDecimal score(CandidateContext candidate, Map<String, RuleCheck> checks) {
		Signals s = Signals.of(candidate, checks);
		return ScoringObjective.bounded(50 + 12 * s.flag(s.sector()) + 2 * s.priorVisits() + 2 * s.flag(s.dialect())
				- 2 * s.visitsThatDay() - 10 * s.flag(s.concern()));
	}

	@Override
	public String reason(CandidateContext candidate, Map<String, RuleCheck> checks) {
		Signals s = Signals.of(candidate, checks);
		if (s.sector()) {
			return "Already works in the elder's sector";
		}
		if (s.priorVisits() > 0) {
			return "Has visited this elder before";
		}
		return "Free at that time, from another sector";
	}
}
