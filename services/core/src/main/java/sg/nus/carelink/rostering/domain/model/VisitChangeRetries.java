package sg.nus.carelink.rostering.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * How long re-rostering keeps sending a change visit has not answered (UC-MG04 saga). After the first
 * try fails it tries again after each wait in turn - one, two, five, ten and twenty minutes by default, so
 * a visit service that restarts is caught up and one that stays down reaches a manager in under forty
 * minutes - and gives up early once the next try would come later than {@code giveUpBefore} the visit's
 * start: by then a manager has to step in, not a timer.
 *
 * @param waits the wait before each retry; as many retries as waits
 * @param giveUpBefore how long before the visit starts retrying stops
 */
public record VisitChangeRetries(List<Duration> waits, Duration giveUpBefore) {

	public static final VisitChangeRetries DEFAULT = new VisitChangeRetries(List.of(Duration.ofMinutes(1),
			Duration.ofMinutes(2), Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(20)),
			Duration.ofHours(1));

	public VisitChangeRetries {
		waits = List.copyOf(waits);
		Objects.requireNonNull(giveUpBefore, "giveUpBefore");
		if (waits.isEmpty() || waits.stream().anyMatch(wait -> wait.isNegative() || wait.isZero())) {
			throw new IllegalArgumentException("At least one retry, and every wait positive");
		}
	}

	/** How long a new step is left to the request that recorded it before the sweep may take it. */
	public Duration firstWait() {
		return waits.get(0);
	}

	/**
	 * When to try again after {@code failures} tries have not got through, or empty to give up: every
	 * retry is spent, or the next would come too close to the visit.
	 */
	public Optional<LocalDateTime> nextTry(int failures, LocalDateTime now, LocalDateTime visitStart) {
		if (failures < 1 || failures > waits.size()) {
			return Optional.empty();
		}
		LocalDateTime next = now.plus(waits.get(failures - 1));
		return next.isBefore(visitStart.minus(giveUpBefore)) ? Optional.of(next) : Optional.empty();
	}
}
