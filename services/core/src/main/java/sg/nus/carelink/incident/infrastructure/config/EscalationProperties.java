package sg.nus.carelink.incident.infrastructure.config;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import sg.nus.carelink.incident.domain.model.Incident;

/**
 * The escalation numbers, as the institution sets them.
 *
 * <p>UC-MG05 states that the countdown is "按严重度分级配置" and UC-MG04 that a response
 * window is "由机构配置，不是代码常量". So they live in {@code application.yml} under
 * {@code carelink.escalation} and are bound here; nothing in the domain layer holds a
 * literal number of minutes.
 *
 * <p>Defaults match {@code EscalationPolicy.defaults()} so that a deployment with no
 * configuration still behaves sensibly, and so that a unit test and a running application
 * agree without the test having to read a file.
 *
 * <p>There is nothing here about shifts: CareLink does not roster its managers, so the
 * chain never asks who is on call.
 */
@ConfigurationProperties(prefix = "carelink.escalation")
public class EscalationProperties {

	/** How long the first level gets, per severity. */
	private Map<Incident.Severity, Duration> countdown = defaultCountdowns();

	/** Each level below the first gets this many times the previous level's countdown. */
	private double laterLevelMultiplier = 2.0;


	/** How often the UC-SYS02 scan looks for expired countdowns. */
	private Duration scanInterval = Duration.ofSeconds(60);

	private static Map<Incident.Severity, Duration> defaultCountdowns() {
		Map<Incident.Severity, Duration> defaults = new EnumMap<>(Incident.Severity.class);
		defaults.put(Incident.Severity.HIGH, Duration.ofMinutes(5));
		defaults.put(Incident.Severity.MEDIUM, Duration.ofMinutes(15));
		defaults.put(Incident.Severity.LOW, Duration.ofMinutes(60));
		return defaults;
	}

	public Map<Incident.Severity, Duration> getCountdown() {
		return countdown;
	}

	public void setCountdown(Map<Incident.Severity, Duration> countdown) {
		Map<Incident.Severity, Duration> merged = defaultCountdowns();
		if (countdown != null) {
			merged.putAll(countdown);
		}
		this.countdown = merged;
	}

	public double getLaterLevelMultiplier() {
		return laterLevelMultiplier;
	}

	public void setLaterLevelMultiplier(double laterLevelMultiplier) {
		this.laterLevelMultiplier = laterLevelMultiplier;
	}


	public Duration getScanInterval() {
		return scanInterval;
	}

	public void setScanInterval(Duration scanInterval) {
		this.scanInterval = scanInterval;
	}
}
