package sg.nus.carelink.rostering.domain.service;

import java.time.LocalDateTime;
import java.util.Objects;

import sg.nus.carelink.rostering.domain.model.VacatedSlot;

/**
 * A visit a caregiver is already booked on. What makes a candidate busy at a given time, and
 * what their day's visit count and hours are made of.
 *
 * @param end never null: a visit with no recorded end is given {@link VacatedSlot#DEFAULT_LENGTH}
 */
public record Booking(Long visitId, Long elderId, Long caregiverId, LocalDateTime start, LocalDateTime end) {

	public Booking {
		Objects.requireNonNull(caregiverId, "caregiverId");
		Objects.requireNonNull(start, "start");
		end = end == null || !end.isAfter(start) ? start.plus(VacatedSlot.DEFAULT_LENGTH) : end;
	}

	/** The booking a slot becomes once somebody is put on it. */
	public static Booking of(VacatedSlot slot, Long caregiverId) {
		return new Booking(slot.visitId(), slot.elderId(), caregiverId, slot.start(), slot.effectiveEnd());
	}

	/** Half-open intervals, so a visit ending at ten and another starting at ten do not clash. */
	public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
		return start.isBefore(otherEnd) && otherStart.isBefore(end);
	}

	public long minutes() {
		return java.time.Duration.between(start.atZone(VacatedSlot.ZONE), end.atZone(VacatedSlot.ZONE)).toMinutes();
	}
}
