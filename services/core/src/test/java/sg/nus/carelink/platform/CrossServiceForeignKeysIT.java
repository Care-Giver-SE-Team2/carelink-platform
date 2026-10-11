package sg.nus.carelink.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import sg.nus.carelink.testsupport.ServiceTables;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * core's database holds core's tables only, now that each service has a database of its own
 * (docs/platform/service-boundaries.md, section 9): platform migration V3 dropped the foreign keys
 * between the services' tables, and V11 to V13 dropped the services' tables. A migration that brings a
 * service's table back into core's database, or a key to a table core does not own - for example one an
 * upstream sync brings in - fails here: put the table in its service's own migrations instead.
 */
@SpringBootTest
class CrossServiceForeignKeysIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, CrossServiceForeignKeysIT.class, null);
	}

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void coresDatabaseHoldsNoServicesTables() {
		Set<String> services = new HashSet<>(ServiceTables.VISIT);
		services.addAll(ServiceTables.REPORT);
		services.addAll(ServiceTables.NOTIFICATION);

		assertThat(tables()).as("tables of visit, report and notification in core's database")
				.doesNotContainAnyElementsOf(services);
	}

	@Test
	void noForeignKeyLeavesCoresTables() {
		List<String> leaving = foreignKeys().stream()
				.filter(key -> !ServiceTables.CORE.contains(key.referencedTable()))
				.map(key -> "%s (%s) -> %s".formatted(key.table(), key.name(), key.referencedTable()))
				.toList();

		assertThat(leaving).as("foreign keys to a table core does not own").isEmpty();
	}

	@Test
	void theKeysWithinCoreStay() {
		assertThat(foreignKeys()).extracting(ForeignKey::name)
				.contains("fk_incident_log_incident", "fk_roster_change_absence");
	}

	private List<String> tables() {
		return jdbc.queryForList(
				"select table_name from information_schema.tables where table_schema = database()", String.class);
	}

	private List<ForeignKey> foreignKeys() {
		return jdbc.query("""
				select constraint_name, table_name, referenced_table_name
				from information_schema.referential_constraints
				where constraint_schema = database()
				""", (row, number) -> new ForeignKey(row.getString(1), row.getString(2), row.getString(3)));
	}

	private record ForeignKey(String name, String table, String referencedTable) {
	}

}
