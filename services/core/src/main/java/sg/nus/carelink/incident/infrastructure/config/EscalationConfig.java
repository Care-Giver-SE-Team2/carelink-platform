package sg.nus.carelink.incident.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.service.EscalationPolicy;

/**
 * Turns the configured escalation numbers into the domain's view of them, and switches on
 * the scheduler that UC-SYS02 needs.
 *
 * <p>This is the only place in the incident module that knows Spring configuration exists.
 * {@link EscalationPolicy} is a plain object, so every escalation rule can still be unit
 * tested by constructing one directly.
 *
 * <p>{@code @EnableScheduling} is declared here rather than on the application class so
 * that the reason for it — the incident response countdown — sits next to the thing it
 * serves.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(EscalationProperties.class)
public class EscalationConfig {

	@Bean
	EscalationPolicy escalationPolicy(EscalationProperties properties) {
		return new EscalationPolicy(properties.getCountdown(), properties.getLaterLevelMultiplier());
	}

	/**
	 * The clock every deadline is measured against.
	 *
	 * <p>Injected rather than called statically so a test can hand the services a fixed
	 * clock and step a countdown over its deadline without sleeping. Fixed to the
	 * institution's zone, because a response deadline is a wall-clock promise to a family,
	 * not a UTC instant.
	 */
	@Bean
	@ConditionalOnMissingBean(Clock.class)
	Clock carelinkClock() {
		return Clock.system(Incident.CARELINK_ZONE);
	}
}
