package sg.nus.carelink.report.support;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.nus.carelink.testsupport.CoreStandIn;

/**
 * core, in every report test that starts the application: the core stand-in instead of the core-api
 * client, which only steps in when no CoreApi is defined. It answers an elder's report profile, a
 * caregiver's name and their leave from the rows the tests lay out in core's tables; a test that
 * needs another answer replaces it with {@code @MockitoBean CoreApi}. Component scanning finds this
 * class; slice tests leave it out, as they leave out every configuration class of the application's own.
 */
@Configuration(proxyBeanMethods = false)
class CoreStandInConfiguration {

	@Bean
	CoreStandIn coreStandIn(JdbcTemplate jdbc, Clock clock) {
		return new CoreStandIn(jdbc, clock);
	}

}
