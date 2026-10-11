package sg.nus.carelink.rostering.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What re-rostering after an absence (UC-MG04) needs from the visits, which visit owns: which ones
 * the absence vacates, who is booked when, who has been to an elder before, and the four changes
 * it can make: give a visit to someone else, leave it uncovered as an exception, call it off, or
 * move it. Rostering asks this port, which it owns; the adapter asks visit's internal API.
 *
 * <p>Every change also writes visit_assignment, so a visit's caregivers stay on record after it
 * changes hands (DECISION 17: "accepting a candidate creates a visit_assignment").
 *
 * <p>The changes take a request key: visit makes a change once per key and answers the same key again
 * with the first answer, so a change that got no answer is sent again with its key, never a new one.
 * visit's refusal comes back as {@link sg.nus.carelink.shared.error.BusinessRuleViolation} (409, with
 * visit's code) and a visit that does not exist as {@link sg.nus.carelink.shared.error.ResourceNotFound}
 * (404); anything else - no connection, no answer in time, a server error - means the change may or may
 * not have been made.
 */
public interface VisitReassignment {

	/** The caregiver's visits nobody has started that begin in [from, until), earliest first. */
	List<VisitSlot> unstartedFor(Long caregiverId, LocalDateTime from, LocalDateTime until);

	Optional<VisitSlot> find(Long visitId);

	/** Visits somebody is booked on that begin in [from, until): busy times, day counts, day hours. */
	List<Booking> bookingsBetween(LocalDateTime from, LocalDateTime until);

	/** Caregiver id to how many visits they finished for this elder in [since, until). */
	Map<Long, Integer> finishedVisitsWith(Long elderId, LocalDateTime since, LocalDateTime until);

	/**
	 * Gives the visit to another caregiver because of an absence.
	 *
	 * @param requestKey makes the change once however often it is sent; null to make it every time
	 */
	void reassign(Long visitId, Long toCaregiverId, Change why, String requestKey);

	/** {@link #reassign(Long, Long, Change, String)} without a key, for a caller that never sends it again. */
	default void reassign(Long visitId, Long toCaregiverId, Change why) {
		reassign(visitId, toCaregiverId, why, null);
	}

	/** Nobody can take the visit: it becomes an exception with nobody on it. */
	void markUncovered(Long visitId, Change why, String requestKey);

	/** Calls the visit off - the family skipped it. */
	void callOff(Long visitId, Change why, String requestKey);

	/**
	 * Moves the visit: this one is called off and a new one is created at {@code newStart}, same
	 * length, with {@code toCaregiverId} on it.
	 *
	 * @return the new visit's id; the same one every time the move is sent with the same key
	 */
	Long moveTo(Long visitId, LocalDateTime newStart, Long toCaregiverId, Change why, String requestKey);

	/**
	 * A visit as re-rostering sees it.
	 *
	 * @param status the visit's state, e.g. SCHEDULED, or EXCEPTION for one an absence left uncovered
	 */
	record VisitSlot(Long visitId, Long elderId, Long caregiverId, Long carePlanId, String serviceType,
			LocalDateTime start, LocalDateTime end, String status, Long absenceId) {
	}

	/** A visit somebody is booked on. */
	record Booking(Long visitId, Long elderId, Long caregiverId, LocalDateTime start, LocalDateTime end) {
	}

	/**
	 * Why a visit is changing, as the assignment history records it.
	 *
	 * @param rosteringCandidateId the suggestion the new caregiver came from, if any
	 */
	record Change(Long absenceId, Long byUserId, Long rosteringCandidateId, String reason) {
	}

}
