package sg.nus.carelink.testsupport;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What every notification test that starts the application has in place of core: the core stand-in
 * instead of the core-api client, which only steps in when no CoreApi is defined, answering who reads
 * an inbox and which elders a family member may read from the accounts and bindings the tests lay out
 * in core's tables; and notification's own database, built by its own migrations beside core's tables
 * (OwnDatabaseForTests), as in a real run. Component scanning finds this class; slice tests leave it
 * out, as they leave out every configuration class of the application's own.
 */
@Configuration(proxyBeanMethods = false)
@Import(OwnDatabaseForTests.class)
class CoreStandInConfiguration {

	@Bean
	CoreStandIn coreStandIn(JdbcTemplate jdbc, Clock clock) {
		return new CoreStandIn(jdbc, clock);
	}

}
