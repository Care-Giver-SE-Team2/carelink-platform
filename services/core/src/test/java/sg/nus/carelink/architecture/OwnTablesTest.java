package sg.nus.carelink.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.testsupport.OwnTables;
import sg.nus.carelink.testsupport.ServiceTables;

/**
 * core's database holds only core's tables once the services have their own, so core's code names no
 * other: it asks visit through VisitApi, and the other services learn of core's changes from its events.
 */
class OwnTablesTest {

	@Test
	void theCodeNamesOnlyCoresOwnTables() {
		Set<String> own = new HashSet<>(ServiceTables.CORE);
		own.addAll(ServiceTables.EACH_SERVICE);

		assertThat(OwnTables.foreignTablesIn(Path.of("src/main/java"), own)).isEmpty();
	}

}
