package sg.nus.carelink.rostering.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * UC-MG06: how many booked visits one certificate puts at risk. A visit is at risk when its
 * caregiver is booked on it, it starts after the last day the certificate covers them, and
 * its care plan requires that certificate's type. Only the roster window can be counted, so a
 * certificate that covers them past the window's end has no count at all rather than zero.
 */
public final class VisitsAtRisk {

	/** A visit someone is booked on and has not started. */
	public record Booking(Long caregiverId, Long carePlanId, LocalDateTime start) {
	}

	private final LocalDateTime now;
	private final LocalDateTime windowEnd;
	private final List<Booking> bookings;
	private final Map<Long, Set<Long>> requiredTypesByPlan;

	/**
	 * @param windowEnd exclusive end of the roster window, the furthest any visit exists
	 * @param requiredTypesByPlan credential type ids each care plan requires
	 */
	public VisitsAtRisk(LocalDateTime now, LocalDateTime windowEnd, List<Booking> bookings,
			Map<Long, Set<Long>> requiredTypesByPlan) {
		this.now = now;
		this.windowEnd = windowEnd;
		this.bookings = List.copyOf(bookings);
		this.requiredTypesByPlan = Map.copyOf(requiredTypesByPlan);
	}

	/**
	 * @param coveredUntil the last day the certificate covers the caregiver
	 * @return the count, or empty when the certificate covers them beyond the roster window
	 */
	public Optional<Integer> count(Long caregiverId, Long credentialTypeId, LocalDate coveredUntil) {
		LocalDateTime lapses = coveredUntil.plusDays(1).atStartOfDay();
		if (!lapses.isBefore(windowEnd)) {
			return Optional.empty();
		}
		LocalDateTime from = lapses.isAfter(now) ? lapses : now;
		int count = (int) bookings.stream()
				.filter(b -> Objects.equals(b.caregiverId(), caregiverId))
				.filter(b -> !b.start().isBefore(from) && b.start().isBefore(windowEnd))
				.filter(b -> requiredTypesByPlan.getOrDefault(b.carePlanId(), Set.of()).contains(credentialTypeId))
				.count();
		return Optional.of(count);
	}
}
