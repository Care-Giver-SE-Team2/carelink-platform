package sg.nus.carelink.testsupport;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.nus.carelink.shared.config.PlatformMigrationsAutoConfiguration;

/**
 * visit's database beside the one a test runs on, for a test that loads demonstration data: the data
 * writes visit's rows into {@code carelink_visit} on the same server (core's db/demo), as it does on a
 * real one. It is built afresh from visit's own migrations, so what another test class left there is
 * gone.
 */
public final class VisitDatabaseForDemoData {

	public static final String NAME = "carelink_visit";

	private VisitDatabaseForDemoData() {
	}

	public static void build(JdbcTemplate jdbc) {
		jdbc.execute("DROP DATABASE IF EXISTS " + NAME);
		// Run from a service's module directory, as the migrations are in the tests; with the events
		// tables too, whose outbox a demonstration script reads
		Flyway visit = Flyway.configure()
				.dataSource(jdbc.getDataSource())
				.schemas(NAME)
				.createSchemas(true)
				.locations("filesystem:../visit/src/main/resources/db/migration")
				.load();
		visit.migrate();
		Flyway.configure()
				.configuration(visit.getConfiguration())
				.locations("filesystem:../../libs/events/src/main/resources/db/platform")
				.table(PlatformMigrationsAutoConfiguration.HISTORY_TABLE)
				.baselineOnMigrate(true)
				.baselineVersion("0")
				.load()
				.migrate();
	}

}
