package sg.nus.carelink.openplatform.application;

import java.net.URI;
import java.util.Map;

/**
 * Sends one webhook request and says what came back. It never throws for what the endpoint did:
 * a timeout, a refused connection or an address CareLink will not call are outcomes like any
 * status.
 */
public interface WebhookTransport {

	Outcome post(URI url, Map<String, String> headers, String body);

	/**
	 * What one request got: the HTTP status, or why there was none.
	 *
	 * @param status the status the endpoint answered; null when it did not
	 * @param error why there was no answer, e.g. {@code TIMEOUT}; null when there was one
	 */
	record Outcome(Integer status, long durationMs, String error) {

		public static Outcome answered(int status, long durationMs) {
			return new Outcome(status, durationMs, null);
		}

		public static Outcome unanswered(String error, long durationMs) {
			return new Outcome(null, durationMs, error);
		}
	}
}
