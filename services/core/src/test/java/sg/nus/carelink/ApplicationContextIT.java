package sg.nus.carelink;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import sg.nus.carelink.identity.domain.repository.AppUserRepository;
import sg.nus.carelink.testsupport.SharedMySql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test covering context startup and the Flyway migrations. It starts a real
 * MySQL container so the migrations are genuinely applied and entity mappings are
 * validated against the resulting schema.
 *
 * <p>Named *IT, so it runs only under mvn verify -Pintegration, in the deep stage of the
 * pipeline. Running it locally requires Docker.
 */
@SpringBootTest
class ApplicationContextIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, ApplicationContextIT.class, null);
	}

	@Autowired
	private DataSource dataSource;

	@Autowired
	private AppUserRepository users;

	@Test
	void migrationsCreateTheBaselineSchema() throws Exception {
		// Asserting on the schema rather than merely on startup: a context that loads but
		// whose migrations silently did nothing would otherwise pass.
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet tables = statement.executeQuery(
						"SELECT table_name FROM information_schema.tables "
								+ "WHERE table_schema = DATABASE()")) {

			var names = new java.util.ArrayList<String>();
			while (tables.next()) {
				names.add(tables.getString(1).toLowerCase());
			}
			assertThat(names).contains("app_user", "user_role", "flyway_schema_history");
		}
	}

	@Test
	void theRepositoryIsWiredToTheDatabase() {
		// Exercises the whole persistence path: domain interface, adapter, Spring Data,
		// JPA mapping, real schema. An entity mapped to a column that the migration never
		// created would fail here.
		assertThat(users.findByUsername("nobody")).isEmpty();
	}
}
