package sg.nus.carelink.report.domain.model;

import java.util.Objects;

/**
 * Something the caregiver wrote down during a visit in the period.
 *
 * <p>The observation summary of UC-MG07 step 2 ("观察摘要"). It is free text in the
 * caregiver's own words, which is exactly why the three readers see it differently: the
 * family reads it, the institution reads it with the visit it came from, and the regulator's
 * version counts it without quoting it.
 *
 * @param task the care task the note was written against, when there was one
 */
public record ObservationFact(Long visitId, String task, String note) {

	public ObservationFact {
		Objects.requireNonNull(visitId, "visitId");
		Objects.requireNonNull(note, "note");
	}
}
