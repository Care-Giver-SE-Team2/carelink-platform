package sg.nus.carelink.rostering.domain.service;

import java.util.Objects;

import sg.nus.carelink.rostering.domain.model.RosteringCandidateCheck;
import sg.nus.carelink.rostering.domain.model.RosteringConstraint;

/**
 * What one rule said about one candidate, with the words the manager is shown next to it:
 * "Nursing expired 2026-08-02", "3 / 8 visits", "Speaks hokkien".
 *
 * <p>For a HARD rule a FAIL excludes the candidate. For a SOFT rule PASS and FAIL only mean
 * for and against; the objective decides how much that matters.
 */
public record RuleCheck(String code, RosteringConstraint.Kind kind, RosteringCandidateCheck.Result result,
		String detail) {

	/** The width of rostering_candidate_check.detail. */
	public static final int DETAIL_LENGTH = 100;

	public RuleCheck {
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(result, "result");
		detail = detail == null || detail.length() <= DETAIL_LENGTH
				? detail
				: detail.substring(0, DETAIL_LENGTH - 3) + "...";
	}

	public static RuleCheck pass(ReplacementRule rule, String detail) {
		return new RuleCheck(rule.code(), rule.kind(), RosteringCandidateCheck.Result.PASS, detail);
	}

	public static RuleCheck fail(ReplacementRule rule, String detail) {
		return new RuleCheck(rule.code(), rule.kind(), RosteringCandidateCheck.Result.FAIL, detail);
	}

	public static RuleCheck notApplicable(ReplacementRule rule, String detail) {
		return new RuleCheck(rule.code(), rule.kind(), RosteringCandidateCheck.Result.NOT_APPLICABLE, detail);
	}

	/** A HARD rule failed: the candidate cannot take the visit. */
	public boolean excludes() {
		return kind == RosteringConstraint.Kind.HARD && result == RosteringCandidateCheck.Result.FAIL;
	}

	public boolean passed() {
		return result == RosteringCandidateCheck.Result.PASS;
	}

	public boolean failed() {
		return result == RosteringCandidateCheck.Result.FAIL;
	}
}
