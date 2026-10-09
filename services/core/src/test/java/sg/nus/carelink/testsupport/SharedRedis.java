package sg.nus.carelink.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;

/**
 * One Redis container for the whole test run, started the first time a test asks for it, for the
 * tests that check what core keeps in Redis.
 */
public final class SharedRedis {

	private static final int PORT = 6379;

	private static GenericContainer<?> server;

	private SharedRedis() {
	}

	public static void register(DynamicPropertyRegistry registry) {
		GenericContainer<?> redis = server();
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(PORT));
	}

	private static synchronized GenericContainer<?> server() {
		if (server == null) {
			server = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(PORT);
			server.start();
		}
		return server;
	}

}
