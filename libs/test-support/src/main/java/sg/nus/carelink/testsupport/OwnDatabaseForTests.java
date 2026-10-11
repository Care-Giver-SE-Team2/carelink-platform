package sg.nus.carelink.testsupport;

import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.nus.carelink.shared.config.PlatformMigrationsAutoConfiguration;

/**
 * For the tests of a service with a database of its own (docs/platform/service-boundaries.md, section 9).
 * The tests' database holds two things:
 * <ul>
 * <li><b>core's tables</b>, as core's migrations leave them in core's database. They stand in for core: the
 * core stand-in answers from them, and the tests lay out accounts, elders and bindings in them.</li>
 * <li><b>the service's own tables</b>, built by the service's own migrations and then the platform's, exactly
 * as a real run builds its database (PlatformMigrationsAutoConfiguration).</li>
 * </ul>
 * Every table that belongs to a service is dropped from core's copy first, so a statement that reached
 * another service's table fails here as it would in a real run. core's migrations keep history tables of
 * their own, so the service's start empty, as in its own database. Import it in the configuration that
 * gives the service's tests the core stand-in:
 *
 * <pre>{@code @Configuration(proxyBeanMethods = false)
 * @Import(OwnDatabaseForTests.class)
 * class CoreStandInConfiguration { ... }}</pre>
 */
@TestConfiguration(proxyBeanMethods = false)
public class OwnDatabaseForTests {

	/** core's migrations in the tests' database, apart from the service's own history. */
	static final String CORE_HISTORY = "core_fixture_history";

	static final String CORE_PLATFORM_HISTORY = "core_fixture_platform_history";

	@Bean
	FlywayMigrationStrategy coreTablesThenOwnMigrations() {
		return flyway -> {
			// Run from the service's module directory, as the application's migrations are
			Flyway.configure()
					.configuration(flyway.getConfiguration())
					.locations("filesystem:../core/src/main/resources/db/migration")
					.table(CORE_HISTORY)
					.load()
					.migrate();
			Flyway.configure()
					.configuration(flyway.getConfiguration())
					// core's platform migrations, and the libraries' (the events tables), as core runs them
					.locations("filesystem:../core/src/main/resources/db/platform",
							PlatformMigrationsAutoConfiguration.LOCATION)
					.table(CORE_PLATFORM_HISTORY)
					.baselineOnMigrate(true)
					.baselineVersion("0")
					.load()
					.migrate();
			dropServiceTables(flyway.getConfiguration().getDataSource());
			Flyway own = Flyway.configure()
					.configuration(flyway.getConfiguration())
					// The database already holds core's tables; the service's history starts at 0
					.baselineOnMigrate(true)
					.baselineVersion("0")
					.load();
			own.migrate();
			PlatformMigrationsAutoConfiguration.platform(own).migrate();
		};
	}

	private static void dropServiceTables(DataSource dataSource) {
		Set<String> tables = new TreeSet<>(ServiceTables.ALL);
		new JdbcTemplate(dataSource).execute((ConnectionCallback<Void>) connection -> {
			try (Statement statement = connection.createStatement()) {
				statement.execute("SET FOREIGN_KEY_CHECKS = 0");
				for (String table : tables) {
					statement.execute("DROP TABLE IF EXISTS `" + table + "`");
				}
				statement.execute("SET FOREIGN_KEY_CHECKS = 1");
			}
			return null;
		});
	}

}
