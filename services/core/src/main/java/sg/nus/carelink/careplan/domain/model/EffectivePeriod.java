package sg.nus.carelink.careplan.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The days a plan version's weekly schedule is in force: from {@code from}, up to but not
 * including {@code until}. A null {@code until} means open-ended (the elder's current plan).
 * An empty period ({@code until} on or before {@code from}) is a version that was replaced or
 * stopped before it ever started, and schedules nothing.
 */
public record EffectivePeriod(LocalDate from, LocalDate until) {

	public EffectivePeriod {
		Objects.requireNonNull(from, "from");
	}

	public boolean isEmpty() {
		return until != null && !until.isAfter(from);
	}
}
