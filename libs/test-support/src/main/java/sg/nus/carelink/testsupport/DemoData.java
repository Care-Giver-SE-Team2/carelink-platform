package sg.nus.carelink.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Loads one of core's demonstration scripts (core's {@code db/demo}) into a service's tests. On a real
 * server a script names each service's tables with that service's database ({@code carelink_visit.visit});
 * in the service's own tests those tables are the test's database, beside core's copy, so the names of
 * the databases given here are taken out first. The script runs on one connection, as it depends on the
 * session variables it sets.
 */
public final class DemoData {

	private DemoData() {
	}

	/**
	 * @param file the script, for example {@code caregiver-slice1.sql}
	 * @param databasesOfThisTest the databases whose tables are this test's own, for example {@code carelink_visit}
	 */
	public static void load(JdbcTemplate jdbc, String file, String... databasesOfThisTest) {
		String script;
		try {
			// Run from a service's module directory, as the migrations are in the tests
			script = Files.readString(Path.of("../core/src/main/resources/db/demo", file), StandardCharsets.UTF_8);
		}
		catch (IOException unreadable) {
			throw new UncheckedIOException(unreadable);
		}
		for (String database : databasesOfThisTest) {
			script = script.replace(database + ".", "");
		}
		ByteArrayResource resource = new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8), file);
		jdbc.execute((ConnectionCallback<Void>) connection -> {
			ScriptUtils.executeSqlScript(connection, resource);
			return null;
		});
	}

}
