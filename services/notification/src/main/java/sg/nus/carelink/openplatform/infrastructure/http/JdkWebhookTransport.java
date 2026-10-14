package sg.nus.carelink.openplatform.infrastructure.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.springframework.stereotype.Component;

import sg.nus.carelink.openplatform.application.WebhookTransport;
import sg.nus.carelink.openplatform.infrastructure.config.WebhookProperties;

/**
 * Sends webhooks with the JDK's own HTTP client (docs/api/webhooks.md, sections 3 and 6). Redirects
 * are not followed: a 3xx is an answer like any other non-2xx, and fails the attempt. The answer's
 * body is never read, so an endpoint that keeps sending cannot hold the attempt open.
 *
 * <p>Only HTTPS to public addresses, unless {@code carelink.webhooks.allow-local-endpoints} is set,
 * as it is for a local run and the tests: see {@link PublicAddresses}.
 */
@Component
class JdkWebhookTransport implements WebhookTransport {

	private final HttpClient client;
	private final WebhookProperties properties;

	JdkWebhookTransport(WebhookProperties properties) {
		this.properties = properties;
		this.client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(properties.connectTimeout())
				.build();
	}

	@Override
	public Outcome post(URI url, Map<String, String> headers, String body) {
		long started = System.nanoTime();
		String refused = refusal(url);
		if (refused != null) {
			return Outcome.unanswered(refused, elapsedMs(started));
		}
		HttpRequest.Builder request = HttpRequest.newBuilder(url)
				.timeout(properties.requestTimeout())
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
		headers.forEach(request::header);
		try {
			HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
			response.body().close();
			return Outcome.answered(response.statusCode(), elapsedMs(started));
		}
		catch (HttpTimeoutException timedOut) {
			return Outcome.unanswered("TIMEOUT", elapsedMs(started));
		}
		catch (SSLException tls) {
			return Outcome.unanswered("TLS_ERROR", elapsedMs(started));
		}
		catch (ConnectException refusedConnection) {
			return Outcome.unanswered("CONNECTION_FAILED", elapsedMs(started));
		}
		catch (IOException io) {
			return Outcome.unanswered("IO_ERROR", elapsedMs(started));
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return Outcome.unanswered("INTERRUPTED", elapsedMs(started));
		}
	}

	/** Why CareLink will not send to {@code url}; null when it will. */
	private String refusal(URI url) {
		if (properties.allowLocalEndpoints()) {
			return null;
		}
		if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null) {
			return "NOT_HTTPS";
		}
		try {
			return PublicAddresses.allPublic(url.getHost()) ? null : "ADDRESS_NOT_ALLOWED";
		}
		catch (UnknownHostException unknown) {
			return "UNKNOWN_HOST";
		}
	}

	private static long elapsedMs(long started) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
	}
}
