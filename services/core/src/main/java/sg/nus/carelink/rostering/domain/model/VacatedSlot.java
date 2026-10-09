package sg.nus.carelink.rostering.domain.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * A visit that needs somebody: who it is for, when, which care plan it serves, and who it is
 * being taken from. What the replacement search is asked about.
 *
 * <p>Rostering's own view of a visit, so the search and its rules depend on nothing outside
 * this module's domain; the application layer builds one from what the visit module says.
 *
 * @param vacatedByCaregiverId the absent caregiver; null for a visit nobody holds
 * @param end null when the plan gave no duration, in which case {@link #DEFAULT_LENGTH} is assumed
 */
public record VacatedSlot(
		Long visitId,
		Long elderId,
		Long carePlanId,
		String serviceType,
		LocalDateTime start,
		LocalDateTime end,
		Long vacatedByCaregiverId) {

	/** What a visit with no recorded end is taken to last, for clashes and daily hours. */
	public static final Duration DEFAULT_LENGTH = Duration.ofMinutes(60);

	/**
	 * The zone every visit time is a wall-clock time in. Lengths are measured in it, so that a
	 * visit's length is what a clock in the elder's home would show.
	 */
	public static final ZoneId ZONE = ZoneId.of("Asia/Singapore");

	public VacatedSlot {
		Objects.requireNonNull(visitId, "visitId");
		Objects.requireNonNull(elderId, "elderId");
		Objects.requireNonNull(start, "start");
	}

	/** The end used for clashes: the recorded one, or the start plus the default length. */
	public LocalDateTime effectiveEnd() {
		return end == null || !end.isAfter(start) ? start.plus(DEFAULT_LENGTH) : end;
	}

	public Duration length() {
		return Duration.between(start.atZone(ZONE), effectiveEnd().atZone(ZONE));
	}

	public LocalDate day() {
		return start.toLocalDate();
	}

	/** The same visit moved to another start, keeping its length (UC-MG04 alternative 4b). */
	public VacatedSlot movedTo(LocalDateTime newStart) {
		return new VacatedSlot(visitId, elderId, carePlanId, serviceType, newStart, newStart.plus(length()),
				vacatedByCaregiverId);
	}
}
