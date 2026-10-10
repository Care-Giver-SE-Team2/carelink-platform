package sg.nus.carelink.testsupport;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * For a service's tests before the schema split. Core's migrations build the schema, and then the
 * platform's tables follow, such as the events library's {@code outbox_event} and
 * {@code consumed_message}. They come from {@code db/platform} in every jar on the classpath and
 * are recorded in a history table of their own, the way core migrates them. Import it in a test
 * that publishes or handles events:
 *
 * <pre>{@code @SpringBootTest
 * @Import(PlatformTablesForTests.class)
 * class SomethingHandledIT { ... }}</pre>
 */
@TestConfiguration(proxyBeanMethods = false)
public class PlatformTablesForTests {

	@Bean
	FlywayMigrationStrategy applicationThenPlatformMigrations() {
		return flyway -> {
			flyway.migrate();
			Flyway.configure()
					.configuration(flyway.getConfiguration())
					.locations("classpath:db/platform")
					.table("flyway_platform_history")
					.baselineOnMigrate(true)
					.baselineVersion("0")
					.load()
					.migrate();
		};
	}

}
