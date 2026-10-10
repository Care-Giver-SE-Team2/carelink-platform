package sg.nus.carelink.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * No foreign key joins two services' tables. The services share one database until the schema
 * split, but each owns its tables (docs/platform/service-boundaries.md, section 1), and platform
 * migration V3 dropped the keys between them, so each service is built as it will run after the
 * split. A migration that adds such a key again, for example one an upstream sync brings in, fails
 * here: drop the key in the next platform migration. A new table counts as core's until it is added
 * to its service's list below.
 */
@SpringBootTest
class CrossServiceForeignKeysIT {

	/** The tables each service other than core owns; every other table is core's or the platform's. */
	private static final Map<String, Set<String>> SERVICE_TABLES = Map.of(
			"visit", Set.of("caregiver_command_receipt", "elder_confirmation", "visit", "visit_assignment",
					"visit_check_in_record", "visit_evidence", "visit_health_record", "visit_missed_check_in_trigger",
					"visit_state_transition", "visit_task", "vital_sign"),
			"report", Set.of("caregiver_review", "report", "report_amendment", "report_basis", "value_added_service",
					"value_added_service_request"),
			"notification", Set.of("notification", "notification_subscription"));

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, CrossServiceForeignKeysIT.class, null);
	}

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void noForeignKeyJoinsTwoServicesTables() {
		List<String> crossing = foreignKeys().stream()
				.filter(key -> !owner(key.table()).equals(owner(key.referencedTable())))
				.map(key -> "%s (%s) -> %s".formatted(key.table(), key.name(), key.referencedTable()))
				.toList();

		assertThat(crossing).as("foreign keys between two services' tables").isEmpty();
	}

	@Test
	void theKeysWithinAServiceStay() {
		assertThat(foreignKeys()).extracting(ForeignKey::name)
				.contains("fk_visit_task_visit", "fk_vital_visit", "fk_incident_log_incident", "fk_roster_change_absence");
	}

	private List<ForeignKey> foreignKeys() {
		return jdbc.query("""
				select constraint_name, table_name, referenced_table_name
				from information_schema.referential_constraints
				where constraint_schema = database()
				""", (row, number) -> new ForeignKey(row.getString(1), row.getString(2), row.getString(3)));
	}

	private static String owner(String table) {
		return SERVICE_TABLES.entrySet().stream()
				.filter(service -> service.getValue().contains(table))
				.map(Map.Entry::getKey)
				.findFirst()
				.orElse("core");
	}

	private record ForeignKey(String name, String table, String referencedTable) {
	}

}
