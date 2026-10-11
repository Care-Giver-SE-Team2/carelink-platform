package sg.nus.carelink.shared.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;

/**
 * How every service migrates its database: its own migrations first ({@code db/migration}, in
 * Flyway's own history table), then the platform's tables from {@code db/platform} in every jar on
 * the classpath - the scheduler locks, and the events tables libs/events ships - recorded in a
 * history table of their own.
 *
 * <p>The application's migrations number on as they please. Because the two version sequences live
 * in different history tables, a platform table can never take a version number the application
 * also uses, which Flyway would refuse at start-up. A service's tests may declare a strategy of their
 * own (libs/test-support), and this one steps aside.
 */
@AutoConfiguration
@ConditionalOnClass({ Flyway.class, FlywayMigrationStrategy.class })
public class PlatformMigrationsAutoConfiguration {

	public static final String LOCATION = "classpath:db/platform";

	public static final String HISTORY_TABLE = "flyway_platform_history";

	@Bean
	@ConditionalOnMissingBean(FlywayMigrationStrategy.class)
	FlywayMigrationStrategy applicationThenPlatformMigrations() {
		return flyway -> {
			flyway.migrate();
			platform(flyway).migrate();
		};
	}

	/** The platform's migrations, configured as the service's own are, in their own history table. */
	public static Flyway platform(Flyway application) {
		return Flyway.configure()
				.configuration(application.getConfiguration())
				.locations(LOCATION)
				.table(HISTORY_TABLE)
				// The schema already holds the application's tables; start this history at 0
				.baselineOnMigrate(true)
				.baselineVersion("0")
				.load();
	}

}
