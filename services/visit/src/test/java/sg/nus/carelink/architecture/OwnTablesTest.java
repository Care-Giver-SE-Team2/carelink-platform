package sg.nus.carelink.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.testsupport.OwnTables;
import sg.nus.carelink.testsupport.ServiceTables;

/**
 * visit's database holds only its own tables, so its code names no other: core's data comes through
 * CoreApi, and the other services learn of visits from visit's events.
 */
class OwnTablesTest {

	@Test
	void theCodeNamesOnlyVisitsOwnTables() {
		Set<String> own = new HashSet<>(ServiceTables.VISIT);
		own.addAll(ServiceTables.EACH_SERVICE);

		assertThat(OwnTables.foreignTablesIn(Path.of("src/main/java"), own)).isEmpty();
	}

}
