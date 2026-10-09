package sg.nus.carelink.rostering.domain.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import sg.nus.carelink.rostering.domain.model.VacatedSlot;

/**
 * UC-MG04 step 3: for one vacated visit, who can take it and in what order.
 *
 * <p>Two collaborators, each standing for something an institution may want to change: the
 * rules (which come from the rostering_constraint table) say who may go, and the objective (a
 * {@link ScoringObjective}, the manager's choice for this run) says who should. The finder
 * itself only runs every rule for every candidate, keeps every answer, and ranks whoever is
 * left.
 *
 * <p>Ties are broken by the lighter day and then by id, so the same facts always give the
 * same order - which a family looking at a suggestion twice is entitled to expect.
 */
public final class ReplacementFinder {

	private final List<ReplacementRule> rules;
	private final ScoringObjective objective;

	public ReplacementFinder(List<ReplacementRule> rules, ScoringObjective objective) {
		this.rules = List.copyOf(rules);
		this.objective = Objects.requireNonNull(objective, "objective");
	}

	public ScoringObjective objective() {
		return objective;
	}

	public Shortlist shortlist(VacatedSlot slot, RosterSnapshot snapshot) {
		ElderCard elder = snapshot.elder(slot.elderId());
		List<Scored> feasible = new ArrayList<>();
		List<Shortlist.Verdict> excluded = new ArrayList<>();

		for (CandidateCard candidate : snapshot.candidates()) {
			CandidateContext context = new CandidateContext(slot, candidate, elder, snapshot);
			List<RuleCheck> checks = rules.stream().map(rule -> rule.check(context)).toList();
			Optional<RuleCheck> exclusion = checks.stream().filter(RuleCheck::excludes).findFirst();

			if (exclusion.isPresent()) {
				excluded.add(new Shortlist.Verdict(candidate.caregiverId(), candidate.name(), null, null,
						exclusion.get().detail(), exclusion.get().code(), checks));
			}
			else {
				Map<String, RuleCheck> byCode = new LinkedHashMap<>();
				checks.forEach(check -> byCode.put(check.code(), check));
				feasible.add(new Scored(new Shortlist.Verdict(candidate.caregiverId(), candidate.name(), null,
						objective.score(context, byCode), objective.reason(context, byCode), null, checks),
						context.visitsThatDay()));
			}
		}

		feasible.sort(Comparator.comparing((Scored s) -> s.verdict().score(), Comparator.reverseOrder())
				.thenComparingInt(Scored::visitsThatDay)
				.thenComparing(s -> s.verdict().caregiverId()));

		List<Shortlist.Verdict> ranked = new ArrayList<>();
		for (int i = 0; i < feasible.size(); i++) {
			ranked.add(feasible.get(i).verdict().ranked(i + 1));
		}
		return new Shortlist(slot, objective.objective(), ranked, excluded);
	}

	/** A suggestion before it has a rank, with the tie-breaker kept beside it. */
	private record Scored(Shortlist.Verdict verdict, int visitsThatDay) {
	}
}
