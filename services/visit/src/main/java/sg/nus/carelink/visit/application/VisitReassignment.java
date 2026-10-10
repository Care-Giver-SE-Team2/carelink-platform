package sg.nus.carelink.visit.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cross-module contract for UC-MG04: what re-rostering after an absence needs from the visits -
 * which ones the absence vacates, who is booked when, who has been to an elder before - and the
 * four changes it can make: give a visit to someone else, leave it uncovered as an exception,
 * call it off, or move it. Rostering imports this interface only, never visit's domain or
 * infrastructure.
 *
 * <p>Every change also writes visit_assignment, so a visit's caregivers stay on record after it
 * changes hands (DECISION 17: "accepting a candidate creates a visit_assignment").
 */
public interface VisitReassignment {

	/** The caregiver's visits nobody has started that begin in [from, until), earliest first. */
	List<VisitSlot> unstartedFor(Long caregiverId, LocalDateTime from, LocalDateTime until);

	Optional<VisitSlot> find(Long visitId);

	/** Visits somebody is booked on that begin in [from, until): busy times, day counts, day hours. */
	List<Booking> bookingsBetween(LocalDateTime from, LocalDateTime until);

	/** Caregiver id to how many visits they finished for this elder in [since, until). */
	Map<Long, Integer> finishedVisitsWith(Long elderId, LocalDateTime since, LocalDateTime until);

	/** Gives the visit to another caregiver because of an absence. */
	void reassign(Long visitId, Long toCaregiverId, Change why);

	/** Nobody can take the visit: it becomes an exception with nobody on it. */
	void markUncovered(Long visitId, Change why);

	/** Calls the visit off - the family skipped it. */
	void callOff(Long visitId, Change why);

	/**
	 * Moves the visit: this one is called off and a new one is created at {@code newStart}, same
	 * length, with {@code toCaregiverId} on it.
	 *
	 * @return the new visit's id
	 */
	Long moveTo(Long visitId, LocalDateTime newStart, Long toCaregiverId, Change why);

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
