package sg.nus.carelink.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.testsupport.OwnTables;
import sg.nus.carelink.testsupport.ServiceTables;

/**
 * report's database holds only its own tables, so its code names no other: core's and visit's data
 * come through their APIs, and the facts its reports are made from from their events.
 */
class OwnTablesTest {

	@Test
	void theCodeNamesOnlyReportsOwnTables() {
		Set<String> own = new HashSet<>(ServiceTables.REPORT);
		own.addAll(ServiceTables.EACH_SERVICE);

		assertThat(OwnTables.foreignTablesIn(Path.of("src/main/java"), own)).isEmpty();
	}

}
