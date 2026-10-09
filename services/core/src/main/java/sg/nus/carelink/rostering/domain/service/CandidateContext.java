package sg.nus.carelink.rostering.domain.service;

import java.util.List;
import java.util.Objects;

import sg.nus.carelink.rostering.domain.model.VacatedSlot;

/**
 * One candidate for one vacated visit, with the facts every rule and objective reads: what
 * else they have that day, and how often they have been to this elder.
 */
public record CandidateContext(VacatedSlot slot, CandidateCard candidate, ElderCard elder, RosterSnapshot snapshot) {

	public CandidateContext {
		Objects.requireNonNull(slot, "slot");
		Objects.requireNonNull(candidate, "candidate");
		Objects.requireNonNull(elder, "elder");
		Objects.requireNonNull(snapshot, "snapshot");
	}

	public Long caregiverId() {
		return candidate.caregiverId();
	}

	/** The candidate's other bookings that day; the visit being filled is not one of them. */
	public List<Booking> sameDayBookings() {
		return snapshot.bookingsOn(candidate.caregiverId(), slot.day(), slot.visitId());
	}

	public int visitsThatDay() {
		return sameDayBookings().size();
	}

	public long minutesThatDay() {
		return sameDayBookings().stream().mapToLong(Booking::minutes).sum();
	}

	public int priorVisits() {
		return elder.priorVisitsBy(candidate.caregiverId());
	}
}
