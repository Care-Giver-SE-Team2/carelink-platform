package sg.nus.carelink.rostering.domain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;

/**
 * The answer to "who can take this visit": everybody who passed every hard rule, best first,
 * and everybody who did not, each with the rule that stopped them. UC-MG04 step 3, "替补候选人及
 * 匹配理由".
 */
public record Shortlist(VacatedSlot slot, RosteringRun.Objective objective, List<Verdict> ranked,
		List<Verdict> excluded) {

	public Shortlist {
		Objects.requireNonNull(slot, "slot");
		ranked = List.copyOf(ranked);
		excluded = List.copyOf(excluded);
	}

	/** The default plan: the best replacement, if anybody can go. */
	public Optional<Verdict> best() {
		return ranked.stream().findFirst();
	}

	public boolean isEmpty() {
		return ranked.isEmpty();
	}

	/** Whether this caregiver passed every hard rule this time. */
	public boolean isFeasible(Long caregiverId) {
		return ranked.stream().anyMatch(verdict -> verdict.caregiverId().equals(caregiverId));
	}

	/** The first {@code n} suggestions: what the family is offered to choose between. */
	public List<Verdict> top(int n) {
		return ranked.subList(0, Math.min(n, ranked.size()));
	}

	/** Every candidate considered, suggestions first, as rostering_candidate keeps them. */
	public List<Verdict> all() {
		List<Verdict> all = new ArrayList<>(ranked);
		all.addAll(excluded);
		return all;
	}

	/**
	 * One candidate's result.
	 *
	 * @param rank 1 for the best suggestion; null when excluded
	 * @param score null when excluded
	 * @param reason the match reason for a suggestion, or why a hard rule excluded them
	 * @param excludedBy the code of the hard rule that excluded them; null for a suggestion
	 */
	public record Verdict(Long caregiverId, String name, Integer rank, BigDecimal score, String reason,
			String excludedBy, List<RuleCheck> checks) {

		public Verdict {
			Objects.requireNonNull(caregiverId, "caregiverId");
			checks = List.copyOf(checks);
		}

		public boolean isSuggested() {
			return excludedBy == null;
		}

		Verdict ranked(int position) {
			return new Verdict(caregiverId, name, position, score, reason, excludedBy, checks);
		}
	}
}
