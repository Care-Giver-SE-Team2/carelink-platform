package sg.nus.carelink.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.testsupport.OwnTables;
import sg.nus.carelink.testsupport.ServiceTables;

/**
 * notification's database holds only its own tables, so its code names no other: core's and the other
 * services' data come through their APIs and events.
 */
class OwnTablesTest {

	@Test
	void theCodeNamesOnlyNotificationsOwnTables() {
		Set<String> own = new HashSet<>(ServiceTables.NOTIFICATION);
		own.addAll(ServiceTables.EACH_SERVICE);

		assertThat(OwnTables.foreignTablesIn(Path.of("src/main/java"), own)).isEmpty();
	}

	/** The check sees SQL in strings and text blocks and JPA's mapping, and not the words of a comment. */
	@Test
	void theCheckFindsTablesWhereverCodeNamesThem() {
		String source = String.join("\n",
				"/** Reads from elder, joins visit into nothing: a comment. */",
				"@Table(name = \"notification\")",
				"class Example {",
				"  String one = \"select id from app_user where id = ?\";",
				"  String two = \"\"\"",
				"      insert into visit_task (id)",
				"      select t.id from report t join elder_family_binding b on b.id = t.id",
				"      \"\"\";",
				"}");

		assertThat(OwnTables.tablesNamedIn(source))
				.contains("notification", "app_user", "visit_task", "report", "elder_family_binding")
				.doesNotContain("elder", "visit");
	}

}
