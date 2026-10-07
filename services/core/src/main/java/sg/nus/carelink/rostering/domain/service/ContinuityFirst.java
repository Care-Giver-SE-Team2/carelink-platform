package sg.nus.carelink.rostering.domain.service;

import java.math.BigDecimal;
import java.util.Map;

import sg.nus.carelink.rostering.domain.model.RosteringRun;

/**
 * "Continuity of caregiver": somebody the elder already knows comes first, then somebody who
 * speaks their dialect or works nearby. The default, because a stranger at the door is the
 * thing an absence most often costs an elder.
 */
final class ContinuityFirst implements ScoringObjective {

	@Override
	public RosteringRun.Objective objective() {
		return RosteringRun.Objective.CONTINUITY;
	}

	@Override
	public BigDecimal score(CandidateContext candidate, Map<String, RuleCheck> checks) {
		Signals s = Signals.of(candidate, checks);
		return ScoringObjective.bounded(50 + 8 * s.priorVisits() + 4 * s.flag(s.dialect()) + 3 * s.flag(s.sector())
				- 2 * s.visitsThatDay() - 10 * s.flag(s.concern()));
	}

	@Override
	public String reason(CandidateContext candidate, Map<String, RuleCheck> checks) {
		Signals s = Signals.of(candidate, checks);
		if (s.priorVisits() > 0) {
			int prior = candidate.priorVisits();
			return "Has visited this elder %d time%s before".formatted(prior, prior == 1 ? "" : "s");
		}
		if (s.dialect()) {
			return "Speaks the elder's dialect";
		}
		if (s.sector()) {
			return "Works in the elder's sector";
		}
		return "Free at that time";
	}
}
