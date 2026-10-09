package sg.nus.carelink.rostering.domain.service;

import sg.nus.carelink.rostering.domain.model.RosteringConstraint;

/**
 * One rule of the replacement search, named by its rostering_constraint code.
 *
 * <p>Every rule runs for every candidate and every answer is kept, which is why the rules are a
 * list and not a chain of responsibility: a chain stops at the first rule that answers, and
 * then "why was she not suggested" would only ever have one reason when there may be three.
 * The candidate is excluded by the first HARD rule that fails, in list order.
 */
public interface ReplacementRule {

	/** The rostering_constraint code this rule implements. */
	String code();

	RosteringConstraint.Kind kind();

	RuleCheck check(CandidateContext candidate);
}
