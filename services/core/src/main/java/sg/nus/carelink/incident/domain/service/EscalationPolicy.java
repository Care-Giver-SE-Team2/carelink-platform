package sg.nus.carelink.incident.domain.service;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import sg.nus.carelink.incident.domain.model.EscalationTier;
import sg.nus.carelink.incident.domain.model.Incident;

/**
 * How long each level gets, by severity.
 *
 * <p>The use case forbids hard-coding these: "倒计时时长按严重度分级配置，不同严重度不同",
 * and the institution is expected to change them without a release. So the numbers arrive
 * from configuration and this object is the domain's view of them — the escalation rules
 * read a policy, never a constant.
 *
 * <p>The tier multiplier is the second half of the same rule. A second-level responder is
 * a safety net rather than a competitor, so that level is given more room than the first;
 * the terminal level has no countdown at all because there is nothing after it to wait for.
 */
public final class EscalationPolicy {

	private final Map<Incident.Severity, Duration> firstLevelCountdown;
	private final double laterLevelMultiplier;

	public EscalationPolicy(Map<Incident.Severity, Duration> firstLevelCountdown, double laterLevelMultiplier) {
		Objects.requireNonNull(firstLevelCountdown, "firstLevelCountdown");
		for (Incident.Severity severity : Incident.Severity.values()) {
			if (!firstLevelCountdown.containsKey(severity)) {
				throw new IllegalArgumentException("no countdown configured for severity " + severity);
			}
		}
		if (laterLevelMultiplier <= 0) {
			throw new IllegalArgumentException("laterLevelMultiplier must be positive");
		}
		this.firstLevelCountdown = new EnumMap<>(firstLevelCountdown);
		this.laterLevelMultiplier = laterLevelMultiplier;
	}

	/** The values used when nothing is configured; also what the unit tests read against. */
	public static EscalationPolicy defaults() {
		Map<Incident.Severity, Duration> countdowns = new EnumMap<>(Incident.Severity.class);
		countdowns.put(Incident.Severity.HIGH, Duration.ofMinutes(5));
		countdowns.put(Incident.Severity.MEDIUM, Duration.ofMinutes(15));
		countdowns.put(Incident.Severity.LOW, Duration.ofMinutes(60));
		return new EscalationPolicy(countdowns, 2.0);
	}

	/**
	 * How long the responder at this position has, for an incident of this severity.
	 *
	 * @param position 1 for the first level that is offered the incident, increasing downwards
	 */
	public Duration countdownFor(Incident.Severity severity, EscalationTier tier, int position) {
		Objects.requireNonNull(severity, "severity");
		if (tier != null && tier.isTerminal()) {
			return Duration.ZERO;
		}
		Duration base = firstLevelCountdown.get(severity);
		if (position <= 1) {
			return base;
		}
		double scaled = base.toSeconds() * Math.pow(laterLevelMultiplier, position - 1.0);
		return Duration.ofSeconds(Math.round(scaled));
	}

	public Duration firstLevelCountdown(Incident.Severity severity) {
		return firstLevelCountdown.get(severity);
	}
}
