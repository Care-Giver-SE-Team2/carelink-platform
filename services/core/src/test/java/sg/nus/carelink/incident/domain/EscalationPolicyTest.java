package sg.nus.carelink.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.EscalationTier;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.service.EscalationPolicy;

/**
 * The countdown rules. They exist as an object rather than as constants because UC-MG05
 * requires the institution to be able to change them without a release.
 */
class EscalationPolicyTest {

	private final EscalationPolicy policy = EscalationPolicy.defaults();

	@Test
	void eachSeverityGetsItsOwnFirstLevelCountdown() {
		assertThat(policy.firstLevelCountdown(Incident.Severity.HIGH)).isEqualTo(Duration.ofMinutes(5));
		assertThat(policy.firstLevelCountdown(Incident.Severity.MEDIUM)).isEqualTo(Duration.ofMinutes(15));
		assertThat(policy.firstLevelCountdown(Incident.Severity.LOW)).isEqualTo(Duration.ofMinutes(60));
	}

	@Test
	void aLevelFurtherDownGetsProportionallyLonger() {
		Duration first = policy.countdownFor(Incident.Severity.HIGH, EscalationTier.FAMILIAR_MANAGER, 1);
		Duration second = policy.countdownFor(Incident.Severity.HIGH, EscalationTier.ANY_MANAGER, 2);

		assertThat(first).isEqualTo(Duration.ofMinutes(5));
		assertThat(second).isEqualTo(Duration.ofMinutes(10));
	}

	@Test
	void theTerminalTierHasNoCountdownBecauseNothingFollowsIt() {
		assertThat(policy.countdownFor(Incident.Severity.HIGH, EscalationTier.FAMILY_ESCALATION, 3))
				.isEqualTo(Duration.ZERO);
	}

	@Test
	void aPositionBelowOneIsTreatedAsTheFirstLevel() {
		assertThat(policy.countdownFor(Incident.Severity.LOW, EscalationTier.FAMILIAR_MANAGER, 0))
				.isEqualTo(Duration.ofMinutes(60));
	}

	@Test
	void aPolicyMissingASeverityIsRejectedAtConstructionRatherThanAtMidnight() {
		Map<Incident.Severity, Duration> incomplete = new EnumMap<>(Incident.Severity.class);
		incomplete.put(Incident.Severity.HIGH, Duration.ofMinutes(5));

		assertThatThrownBy(() -> new EscalationPolicy(incomplete, 2.0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("no countdown configured");
	}

	@Test
	void aMultiplierThatWouldShortenLaterLevelsIntoNothingIsRejected() {
		Map<Incident.Severity, Duration> countdowns = new EnumMap<>(Incident.Severity.class);
		for (Incident.Severity severity : Incident.Severity.values()) {
			countdowns.put(severity, Duration.ofMinutes(5));
		}

		assertThatThrownBy(() -> new EscalationPolicy(countdowns, 0))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
