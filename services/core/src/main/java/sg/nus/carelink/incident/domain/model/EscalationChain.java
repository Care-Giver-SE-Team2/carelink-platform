package sg.nus.carelink.incident.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The ordered set of responders an incident will be offered to, assembled for one incident
 * at one moment.
 *
 * <p>It is deliberately <strong>not</strong> persisted. The use case is explicit that the
 * chain is built at run time from severity, time of day and who is on duty, so writing it
 * down would freeze a decision that is supposed to follow the roster. What is written down
 * is every hand-off, in {@code incident_log}: the timeline is the record of what happened,
 * the chain is the plan for what happens next.
 *
 * <p>Consequence worth knowing: asking for the chain twice can give different answers if
 * the roster changed in between. That is correct behaviour, not a bug, and it is why the
 * API returns {@code assembledAt} alongside the levels.
 */
public record EscalationChain(
		Long incidentId,
		LocalDateTime assembledAt,
		Incident.Severity severity,
		String assembledFrom,
		List<EscalationLevel> levels) {

	public EscalationChain {
		Objects.requireNonNull(assembledAt, "assembledAt");
		Objects.requireNonNull(severity, "severity");
		levels = List.copyOf(Objects.requireNonNull(levels, "levels"));
		if (levels.isEmpty()) {
			throw new IllegalArgumentException("an escalation chain always has at least the terminal level");
		}
	}

	/**
	 * The first level that can actually take the incident, starting at {@code from}.
	 * Levels nobody fills are stepped over; the reason stays visible in the chain.
	 */
	public Optional<EscalationLevel> nextFillableFrom(int from) {
		return levels.stream()
				.filter(level -> level.position() >= from)
				.filter(EscalationLevel::isFillable)
				.findFirst();
	}

	/** The level that follows the one currently holding the incident. */
	public Optional<EscalationLevel> after(EscalationLevel level) {
		return nextFillableFrom(level.position() + 1);
	}

	/** True when no level below {@code from} has anybody in it. */
	public boolean isExhaustedFrom(int from) {
		return nextFillableFrom(from).isEmpty();
	}

	public EscalationLevel terminalLevel() {
		return levels.get(levels.size() - 1);
	}

	/** Which level, if any, is currently holding the incident. */
	public Optional<EscalationLevel> current() {
		return levels.stream()
				.filter(level -> level.state() == EscalationLevel.State.CURRENT)
				.findFirst();
	}

	/** Replaces one level in place, keeping the order. Used as the chain is walked. */
	public EscalationChain with(EscalationLevel replacement) {
		List<EscalationLevel> updated = levels.stream()
				.map(level -> level.position() == replacement.position() ? replacement : level)
				.toList();
		return new EscalationChain(incidentId, assembledAt, severity, assembledFrom, updated);
	}

	public int size() {
		return levels.size();
	}
}
