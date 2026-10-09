package sg.nus.carelink.platform;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The platform's own tables (the scheduler locks now, the outbox later) are migrated from
 * {@code db/platform} and recorded in a history table of their own, right after the application's
 * migrations.
 *
 * <p>The application's migrations keep numbering V24, V25 and on. Because the two version sequences
 * live in different history tables, a platform table can never take a version number the
 * application also uses, which Flyway would refuse at start-up.
 */
@Configuration(proxyBeanMethods = false)
class PlatformMigrations {

	static final String LOCATION = "classpath:db/platform";

	static final String HISTORY_TABLE = "flyway_platform_history";

	@Bean
	FlywayMigrationStrategy applicationThenPlatformMigrations() {
		return flyway -> {
			flyway.migrate();
			Flyway.configure()
					.configuration(flyway.getConfiguration())
					.locations(LOCATION)
					.table(HISTORY_TABLE)
					// The schema already holds the application's tables; start this history at 0
					.baselineOnMigrate(true)
					.baselineVersion("0")
					.load()
					.migrate();
		};
	}

}
