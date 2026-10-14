package sg.nus.carelink.openplatform.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.openplatform.application.WebhookTransport.Outcome;
import sg.nus.carelink.openplatform.infrastructure.config.WebhookProperties;

/**
 * The webhook request as an endpoint receives it, against a real HTTP server on this machine:
 * the body and headers as given, the status answered, redirects not followed, a slow endpoint
 * timed out, an absent one refused, and private addresses refused unless local endpoints are
 * allowed.
 */
class JdkWebhookTransportTest {

	private static final WebhookProperties LOCAL = properties(true, Duration.ofMillis(500));

	private HttpServer server;
	private final List<Received> received = new CopyOnWriteArrayList<>();

	record Received(String method, String path, Map<String, List<String>> headers, String body) {
	}

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/ok", exchange -> answer(exchange, 204));
		server.createContext("/broken", exchange -> answer(exchange, 500));
		server.createContext("/moved", exchange -> {
			exchange.getResponseHeaders().add("Location", url("/ok").toString());
			answer(exchange, 302);
		});
		server.createContext("/slow", exchange -> {
			try {
				Thread.sleep(2_000);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			answer(exchange, 200);
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	@Test
	void postsTheBodyAndHeadersAsGivenAndReturnsTheStatus() {
		Outcome outcome = new JdkWebhookTransport(LOCAL).post(url("/ok"),
				Map.of("Content-Type", "application/json", "X-CareLink-Delivery", "dlv_test"), "{\"id\":\"dlv_test\"}");

		assertThat(outcome.status()).isEqualTo(204);
		assertThat(outcome.error()).isNull();
		assertThat(received).singleElement().satisfies(request -> {
			assertThat(request.method()).isEqualTo("POST");
			assertThat(request.body()).isEqualTo("{\"id\":\"dlv_test\"}");
			assertThat(request.headers().get("Content-type")).containsExactly("application/json");
			assertThat(request.headers().get("X-carelink-delivery")).containsExactly("dlv_test");
		});
	}

	@Test
	void anErrorStatusIsReturnedAsTheAnswer() {
		assertThat(new JdkWebhookTransport(LOCAL).post(url("/broken"), Map.of(), "{}").status()).isEqualTo(500);
	}

	@Test
	void aRedirectIsNotFollowed() {
		Outcome outcome = new JdkWebhookTransport(LOCAL).post(url("/moved"), Map.of(), "{}");

		assertThat(outcome.status()).isEqualTo(302);
		assertThat(received).extracting(Received::path).containsExactly("/moved");
	}

	@Test
	void anEndpointSlowerThanTheTimeoutIsUnanswered() {
		Outcome outcome = new JdkWebhookTransport(LOCAL).post(url("/slow"), Map.of(), "{}");

		assertThat(outcome.status()).isNull();
		assertThat(outcome.error()).isEqualTo("TIMEOUT");
		assertThat(outcome.durationMs()).isBetween(400L, 1_900L);
	}

	@Test
	void anEndpointNobodyListensOnIsUnanswered() throws IOException {
		int closed;
		try (ServerSocket socket = new ServerSocket(0)) {
			closed = socket.getLocalPort();
		}

		Outcome outcome = new JdkWebhookTransport(LOCAL)
				.post(URI.create("http://127.0.0.1:" + closed + "/hook"), Map.of(), "{}");

		assertThat(outcome.status()).isNull();
		assertThat(outcome.error()).isIn("CONNECTION_FAILED", "IO_ERROR");
	}

	@Test
	void inTheCloudOnlyHttpsToPublicAddressesIsSent() {
		JdkWebhookTransport cloud = new JdkWebhookTransport(properties(false, Duration.ofSeconds(1)));

		assertThat(cloud.post(url("/ok"), Map.of(), "{}").error()).isEqualTo("NOT_HTTPS");
		assertThat(cloud.post(URI.create("https://127.0.0.1:" + server.getAddress().getPort() + "/ok"), Map.of(), "{}")
				.error()).isEqualTo("ADDRESS_NOT_ALLOWED");
		assertThat(cloud.post(URI.create("https://169.254.169.254/latest/meta-data/"), Map.of(), "{}").error())
				.isEqualTo("ADDRESS_NOT_ALLOWED");
		assertThat(cloud.post(URI.create("https://10.0.12.7/hook"), Map.of(), "{}").error())
				.isEqualTo("ADDRESS_NOT_ALLOWED");
		assertThat(cloud.post(URI.create("https://name.invalid/hook"), Map.of(), "{}").error())
				.isEqualTo("UNKNOWN_HOST");
		assertThat(received).isEmpty();
	}

	private URI url(String path) {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
	}

	private void answer(HttpExchange exchange, int status) throws IOException {
		received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
				Map.copyOf(exchange.getRequestHeaders()),
				new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
		exchange.sendResponseHeaders(status, -1);
		exchange.close();
	}

	private static WebhookProperties properties(boolean allowLocal, Duration timeout) {
		return new WebhookProperties(allowLocal, Duration.ofMillis(500), timeout, null);
	}
}
