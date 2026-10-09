package sg.nus.carelink.rostering.application;

import java.time.Duration;
import java.util.Objects;

/**
 * How far back the replacement search looks (UC-MG04): a caregiver's finished visits to an elder
 * count as knowing them over {@code continuity}, and spot-check conclusions (UC-MG08) count over
 * {@code spotChecks}.
 */
public record RosterLookbacks(Duration continuity, Duration spotChecks) {

	public static final RosterLookbacks DEFAULT = new RosterLookbacks(Duration.ofDays(180), Duration.ofDays(90));

	public RosterLookbacks {
		Objects.requireNonNull(continuity, "continuity");
		Objects.requireNonNull(spotChecks, "spotChecks");
	}
}
