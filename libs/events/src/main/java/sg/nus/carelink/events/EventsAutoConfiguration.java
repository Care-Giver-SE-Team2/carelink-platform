package sg.nus.carelink.events;

import java.time.Clock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link Events} for every service that depends on this library. The relay and the consumer have
 * auto-configurations of their own, each switched on by its setting.
 *
 * <p>The three are separate top-level classes on purpose: core and the services scan
 * {@code sg.nus.carelink}, and component scanning would pick up a nested configuration class.
 */
@AutoConfiguration
@ConditionalOnClass(JdbcTemplate.class)
@EnableConfigurationProperties(EventsProperties.class)
public class EventsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	Events events(JdbcTemplate jdbc, JsonMapper json, EventsProperties properties, Environment environment) {
		return new JdbcEvents(jdbc, json, serviceName(properties, environment), Clock.systemUTC());
	}

	/** This service's name: {@code carelink.events.service}, or else {@code spring.application.name}. */
	static String serviceName(EventsProperties properties, Environment environment) {
		String name = StringUtils.hasText(properties.service()) ? properties.service()
				: environment.getProperty("spring.application.name");
		if (!StringUtils.hasText(name)) {
			throw new IllegalStateException("Set carelink.events.service to this service's name");
		}
		return name;
	}

}
