package sg.nus.carelink.shared.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * The beans the shared classes need and a service may not declare itself. In core, incident's
 * configuration declares the clock, so this one steps aside.
 */
@AutoConfiguration
public class SharedAutoConfiguration {

	/** The institution's clock: deadlines and audit times are wall-clock times in Singapore. */
	@Bean
	@ConditionalOnMissingBean(Clock.class)
	Clock sharedClock() {
		return Clock.system(ZoneId.of("Asia/Singapore"));
	}

}
