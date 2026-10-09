package sg.nus.carelink.rostering.domain.model;

import java.math.BigDecimal;

/**
 * One caregiver considered for one visit in one run, suggested or excluded (rostering_candidate).
 * "Suggestion 1 of 4" is {@code optionRank} 1; an excluded candidate has no rank and names the
 * hard rule that excluded them. The one put on the visit becomes SELECTED.
 *
 * @param matchReason the reason shown beside a suggestion, or why an excluded one could not go
 */
public record RosteringCandidate(
		Long id,
		Long rosteringRunId,
		Long visitId,
		Long caregiverId,
		Integer optionRank,
		BigDecimal score,
		RosteringCandidate.Outcome outcome,
		String excludedByCode,
		String matchReason) {

	/** The width of rostering_candidate.match_reason. */
	public static final int REASON_LENGTH = 120;

	public RosteringCandidate {
		matchReason = matchReason == null || matchReason.length() <= REASON_LENGTH
				? matchReason
				: matchReason.substring(0, REASON_LENGTH - 3) + "...";
	}

	/** This candidate is the one put on the visit. */
	public RosteringCandidate selected() {
		return new RosteringCandidate(id, rosteringRunId, visitId, caregiverId, optionRank, score, Outcome.SELECTED,
				excludedByCode, matchReason);
	}

	public boolean isSuggestion() {
		return outcome != Outcome.EXCLUDED;
	}

	public enum Outcome {
		SELECTED, SUGGESTED, EXCLUDED
	}
}
