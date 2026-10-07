package sg.nus.carelink.incident.domain.repository;

import sg.nus.carelink.incident.domain.model.SpotCheck;

/**
 * Telling people about a spot check (UC-MG08).
 *
 * <p>The family is asked first and told each time the request changes, because nobody may come
 * to watch without them knowing. The caregiver hears only once the conclusion is recorded: a
 * check announced to the person being checked would watch a rehearsal, not a visit. The
 * manager who asked hears the family's answer.
 */
public interface SpotCheckAlert {

	/** Steps 2 and 4b: the family is asked to agree to a check of this visit. */
	void approvalRequested(SpotCheck check, Names names);

	/** Step 3 or 3a: the manager who asked hears the family's answer. */
	void familyAnswered(SpotCheck check, Names names);

	/** Step 5: the conclusion goes to the family and to the caregiver who was checked. */
	void concluded(SpotCheck check, Names names);

	/** The manager called the request off; the family hears it is not going ahead. */
	void withdrawn(SpotCheck check, Names names);

	/** The words a message needs that the check itself does not hold. */
	record Names(String elderName, String caregiverName) {
	}
}
