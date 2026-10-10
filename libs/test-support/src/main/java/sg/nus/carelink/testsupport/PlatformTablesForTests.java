package sg.nus.carelink.testsupport;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * For a service's tests before the schema split: the database as core leaves it in production.
 * Core's migrations build the schema, and then the platform's migrations follow, recorded in a
 * history table of their own, the way core runs them: core's own ({@code db/platform} in core: the
 * scheduler locks, and V3, which drops the foreign keys between services' tables), and those in
 * every jar on the classpath, such as the events library's {@code outbox_event} and
 * {@code consumed_message}. Import it in a service's integration tests:
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
					// Run from the service's module directory, as the application's migrations are
					.locations("classpath:db/platform", "filesystem:../core/src/main/resources/db/platform")
					.table("flyway_platform_history")
					.baselineOnMigrate(true)
					.baselineVersion("0")
					.load()
					.migrate();
		};
	}

}
