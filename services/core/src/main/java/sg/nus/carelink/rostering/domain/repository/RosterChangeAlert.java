package sg.nus.carelink.rostering.domain.repository;

import java.time.LocalDateTime;

import sg.nus.carelink.rostering.domain.model.RosterChange;

/**
 * Telling people about a change to a visit an absence vacated (UC-MG04).
 *
 * <p>The use case's rule is that one change reaches all three ends - caregiver, elder and family
 * - so {@link #settled} has no option to leave anybody out. The family also hears earlier, when
 * they are asked to choose and when nobody could be found, so that the answer they get from the
 * institution is never silence.
 */
public interface RosterChangeAlert {

	/** Step 4: the family is asked to choose, with the suggestion and the deadline. */
	void offered(RosterChange change, Notice notice);

	/** Exception 3a: nobody is free yet; the family hears the institution is on it. */
	void coordinating(RosterChange change, Notice notice);

	/**
	 * Step 6: the change is in effect. The caregiver who now has the visit, the absent caregiver
	 * it was taken from, the elder and the family are all told.
	 */
	void settled(RosterChange change, Notice notice);

	/**
	 * The words a message needs that the change itself does not hold.
	 *
	 * @param caregiverName who was suggested, or who now has the visit
	 * @param newStart the new time, when the visit was moved
	 * @param why a sentence for the family when the default plan or a fallback decided
	 */
	record Notice(String elderName, String caregiverName, Long caregiverId, LocalDateTime newStart, String why) {
	}
}
