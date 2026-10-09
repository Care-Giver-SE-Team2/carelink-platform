package sg.nus.carelink.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Business thresholds in one place, rather than scattered as constants across modules.
 *
 * <p>These numbers are part of the requirements (late arrival, escalation timeout,
 * report look-back window). They are configuration rather than constants so that they
 * can be adjusted during a demonstration and so that tests can inject different values.
 */
@ConfigurationProperties(prefix = "carelink")
public record CareLinkProperties(

		/** A visit with no check-in after this long counts as late. */
		@DefaultValue("10m") Duration lateArrivalThreshold,

		/** An unhandled incident escalates automatically after this long. */
		@DefaultValue("2h") Duration incidentEscalationDelay,

		/** Look-back window for weekly reports and vital-sign trends. */
		@DefaultValue("30d") Duration reportLookbackWindow
) {
}
