package sg.nus.carelink.rostering.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import sg.nus.carelink.rostering.domain.model.RosteringCandidate;
import sg.nus.carelink.rostering.domain.model.RosteringCandidateCheck;
import sg.nus.carelink.rostering.domain.model.RosteringConstraint;
import sg.nus.carelink.rostering.domain.repository.RosteringCandidateCheckRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringCandidateRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringConstraintRepository;
import sg.nus.carelink.rostering.domain.service.ReplacementRule;
import sg.nus.carelink.rostering.domain.service.ReplacementRules;
import sg.nus.carelink.rostering.domain.service.RuleCheck;
import sg.nus.carelink.rostering.domain.service.Shortlist;

/**
 * How a replacement search is set up and kept: the rules as the table configures them, and
 * every candidate and rule result a search produced, so "why was she not suggested" has an
 * answer afterwards. Shared by every search that re-rosters a visit - the manager's and the
 * family's in {@link AbsenceReRosteringService}, and a cover carrying on in
 * {@link LeaveCoverService} - so they all read the rules and record a run the same way.
 *
 * <p>Not a Spring bean: each service builds one from the repositories it is given.
 */
final class ReplacementSearchRecord {

	private final RosteringConstraintRepository constraints;
	private final RosteringCandidateRepository candidates;
	private final RosteringCandidateCheckRepository checks;

	ReplacementSearchRecord(RosteringConstraintRepository constraints, RosteringCandidateRepository candidates,
			RosteringCandidateCheckRepository checks) {
		this.constraints = constraints;
		this.candidates = candidates;
		this.checks = checks;
	}

	/**
	 * The rules as the table configures them. A table with no rows - a database nobody migrated
	 * past V11 - falls back to the defaults rather than letting everybody through.
	 */
	RuleSet ruleSet() {
		List<RosteringConstraint> rows = constraints.findAll();
		if (rows.isEmpty()) {
			return new RuleSet(ReplacementRules.defaults(), Map.of());
		}
		return new RuleSet(ReplacementRules.from(rows),
				rows.stream().collect(Collectors.toMap(RosteringConstraint::code, RosteringConstraint::id, (a, b) -> a)));
	}

	/**
	 * Keeps every candidate the search considered and every rule result behind them.
	 *
	 * @return caregiver id to the id of their rostering_candidate row
	 */
	Map<Long, Long> record(Long runId, Shortlist shortlist, RuleSet rules) {
		return record(runId, shortlist.slot().visitId(), shortlist, rules);
	}

	/** The same, filed under {@code visitId}: the visit at its new time, once it has been moved. */
	Map<Long, Long> record(Long runId, Long visitId, Shortlist shortlist, RuleSet rules) {
		Map<Long, Long> ids = new HashMap<>();
		for (Shortlist.Verdict verdict : shortlist.all()) {
			RosteringCandidate saved = candidates.save(new RosteringCandidate(null, runId, visitId,
					verdict.caregiverId(), verdict.rank(), verdict.score(),
					verdict.isSuggested() ? RosteringCandidate.Outcome.SUGGESTED : RosteringCandidate.Outcome.EXCLUDED,
					verdict.excludedBy(), verdict.reason()));
			ids.put(verdict.caregiverId(), saved.id());
			for (RuleCheck check : verdict.checks()) {
				Long constraintId = rules.constraintIds().get(check.code());
				if (constraintId != null) {
					checks.save(new RosteringCandidateCheck(null, saved.id(), constraintId, check.result(), check.detail()));
				}
			}
		}
		return ids;
	}

	/** Marks the candidate who took the visit; nothing when there is no row for them. */
	void select(Long candidateId) {
		if (candidateId != null) {
			candidates.findById(candidateId).ifPresent(candidate -> candidates.save(candidate.selected()));
		}
	}

	/** The rules a search runs, and each rule's rostering_constraint id for recording its result. */
	record RuleSet(List<ReplacementRule> rules, Map<String, Long> constraintIds) {
	}
}
