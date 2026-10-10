package sg.nus.carelink.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.mysql.MySQLContainer;

/**
 * One MySQL container per server time zone for the whole integration run, and a fresh, empty
 * database in it for every test class.
 *
 * <p>Starting a container takes six to fifteen seconds on a CI runner, and each *IT used to
 * start its own, which was most of what the integration stage spent its time on. Sharing the
 * server keeps what the tests relied on: each class still gets a database nobody else has
 * written to, migrated by Flyway when its context starts, so ids, counts and seeds behave
 * exactly as they did in a container of their own.
 *
 * <p>Use it from a {@code @DynamicPropertySource} method in place of a {@code @Container
 * @ServiceConnection} field:
 *
 * <pre>{@code
 * @DynamicPropertySource
 * static void database(DynamicPropertyRegistry registry) {
 *     SharedMySql.register(registry, FamilyVisitListIT.class, "+05:00");
 * }
 * }</pre>
 *
 * <p>The server time zone is a server setting, so classes that need a different one get a
 * different container ({@code null} keeps MySQL's default, UTC). JDBC URL parameters such as
 * {@code connectionTimeZone=Asia/Singapore} are per class. The containers are never stopped
 * here; Testcontainers removes them when the JVM exits.
 */
public final class SharedMySql {

	private static final String IMAGE = "mysql:8.4";

	/**
	 * Spring keeps up to 32 test contexts cached, each holding a full connection pool open to
	 * its own database on the shared server; MySQL's default of 151 connections runs out
	 * after about fifteen of them.
	 */
	private static final String MAX_CONNECTIONS = "--max-connections=1000";

	private static final Map<String, MySQLContainer> SERVERS = new ConcurrentHashMap<>();
	private static final AtomicInteger DATABASES = new AtomicInteger();

	private SharedMySql() {
	}

	/**
	 * Creates an empty database for {@code testClass} on the server running in
	 * {@code serverTimeZone}, and points the application's datasource at it.
	 *
	 * @param serverTimeZone the server's {@code --default-time-zone}, e.g. "+05:00"; null for UTC
	 * @param urlParams extra JDBC URL parameters, each "name=value"
	 */
	public static void register(DynamicPropertyRegistry registry, Class<?> testClass, String serverTimeZone,
			String... urlParams) {
		MySQLContainer server = server(serverTimeZone);
		String database = "it_" + testClass.getSimpleName().toLowerCase(Locale.ROOT) + "_" + DATABASES.incrementAndGet();
		execute(server, "CREATE DATABASE " + database);

		StringBuilder url = new StringBuilder("jdbc:mysql://").append(server.getHost()).append(':')
				.append(server.getMappedPort(MySQLContainer.MYSQL_PORT)).append('/').append(database)
				.append("?useSSL=false&allowPublicKeyRetrieval=true");
		for (String param : urlParams) {
			url.append('&').append(param);
		}
		registry.add("spring.datasource.url", url::toString);
		registry.add("spring.datasource.username", server::getUsername);
		registry.add("spring.datasource.password", server::getPassword);
	}

	@SuppressWarnings("resource") // Shared for the whole run; Testcontainers removes it at JVM exit.
	private static MySQLContainer server(String serverTimeZone) {
		return SERVERS.computeIfAbsent(serverTimeZone == null ? "default" : serverTimeZone, key -> {
			MySQLContainer container = serverTimeZone == null
					? new MySQLContainer(IMAGE).withCommand(MAX_CONNECTIONS)
					: new MySQLContainer(IMAGE).withCommand("--default-time-zone=" + serverTimeZone, MAX_CONNECTIONS);
			container.start();
			// The test user only owns the image's own database; let it use the ones made here.
			execute(container, "GRANT ALL PRIVILEGES ON *.* TO '" + container.getUsername() + "'@'%'");
			return container;
		});
	}

	private static void execute(MySQLContainer server, String sql) {
		try (Connection connection = DriverManager.getConnection(server.getJdbcUrl(), "root", server.getPassword());
				Statement statement = connection.createStatement()) {
			statement.execute(sql);
		} catch (SQLException e) {
			throw new IllegalStateException("Could not run on the shared MySQL server: " + sql, e);
		}
	}
}
