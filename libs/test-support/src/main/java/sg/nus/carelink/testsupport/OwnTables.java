package sg.nus.carelink.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds every table a service's code names - in SQL inside its strings and text blocks, and in JPA's
 * {@code @Table} - that is not one of its own. A service with a database of its own has only its own
 * tables there; its tests have core's beside them (OwnDatabaseForTests), so a statement on one of
 * core's tables would pass the tests and fail in a real run. This check catches it at build time.
 *
 * <pre>{@code @Test
 * void theCodeNamesOnlyNotificationsTables() {
 *     assertThat(OwnTables.foreignTablesIn(Path.of("src/main/java"), OWN)).isEmpty();
 * }}</pre>
 */
public final class OwnTables {

	private static final Pattern TEXT_BLOCK = Pattern.compile("\"\"\"(.*?)\"\"\"", Pattern.DOTALL);
	private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
	private static final Pattern SQL_TABLE = Pattern.compile(
			"\\b(?:from|join|into|update|table)\\s+`?([a-z_][a-z0-9_]*)`?", Pattern.CASE_INSENSITIVE);
	private static final Pattern JPA_TABLE = Pattern.compile("@Table\\s*\\(\\s*name\\s*=\\s*\"([a-zA-Z_][a-zA-Z0-9_]*)\"");

	private OwnTables() {
	}

	/** Each place the sources under {@code root} name a known table outside {@code own}, as "File.java: table". */
	public static List<String> foreignTablesIn(Path root, Set<String> own) {
		List<String> found = new ArrayList<>();
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
				String source = Files.readString(file, StandardCharsets.UTF_8);
				for (String table : tablesNamedIn(source)) {
					if (ServiceTables.KNOWN.contains(table) && !own.contains(table)) {
						found.add(file.getFileName() + ": " + table);
					}
				}
			}
		}
		catch (IOException unreadable) {
			throw new UncheckedIOException(unreadable);
		}
		return found;
	}

	/** The tables the SQL in a source's strings and its {@code @Table} mappings name; comments do not count. */
	public static Set<String> tablesNamedIn(String source) {
		Set<String> tables = new java.util.TreeSet<>();
		Matcher blocks = TEXT_BLOCK.matcher(source);
		StringBuilder rest = new StringBuilder();
		int last = 0;
		while (blocks.find()) {
			addSqlTables(blocks.group(1), tables);
			rest.append(source, last, blocks.start());
			last = blocks.end();
		}
		rest.append(source.substring(last));
		Matcher strings = STRING.matcher(rest);
		while (strings.find()) {
			addSqlTables(strings.group(1), tables);
		}
		Matcher jpa = JPA_TABLE.matcher(source);
		while (jpa.find()) {
			tables.add(jpa.group(1).toLowerCase(Locale.ROOT));
		}
		return tables;
	}

	private static void addSqlTables(String text, Set<String> tables) {
		Matcher sql = SQL_TABLE.matcher(text);
		while (sql.find()) {
			tables.add(sql.group(1).toLowerCase(Locale.ROOT));
		}
	}

}
