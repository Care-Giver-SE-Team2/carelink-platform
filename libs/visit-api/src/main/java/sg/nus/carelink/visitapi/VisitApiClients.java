package sg.nus.carelink.visitapi;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds a {@link VisitApi} client on a {@link RestClient.Builder}, with visit's error responses
 * turned back into the exceptions the calls raised in-process. The same mapping as core-api's client.
 */
public final class VisitApiClients {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private VisitApiClients() {
	}

	/** The builder carries the base URL, timeouts and anything else the caller needs. */
	public static VisitApi create(RestClient.Builder builder) {
		RestClient client = builder.defaultStatusHandler(HttpStatusCode::isError, VisitApiClients::raise).build();
		return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build().createClient(VisitApi.class);
	}

	private static void raise(HttpRequest request, ClientHttpResponse response) throws IOException {
		byte[] body = response.getBody().readAllBytes();
		JsonNode problem = problem(body);
		String detail = text(problem, "detail", request.getMethod() + " " + request.getURI().getPath());
		switch (response.getStatusCode().value()) {
			case 403 -> throw new AccessDeniedException(detail);
			case 404 -> throw new VisitNotFound(detail);
			case 409 -> throw new VisitRuleViolation(text(problem, "code", "UNKNOWN"), detail);
			default -> throw new RestClientResponseException(
					"visit answered " + response.getStatusCode().value() + " to " + request.getMethod() + " "
							+ request.getURI().getPath(),
					response.getStatusCode(), response.getStatusText(), response.getHeaders(), body, null);
		}
	}

	private static JsonNode problem(byte[] body) {
		try {
			return body.length == 0 ? null : JSON.readTree(body);
		}
		catch (RuntimeException notJson) {
			return null;
		}
	}

	private static String text(JsonNode problem, String field, String fallback) {
		JsonNode value = problem == null ? null : problem.get(field);
		return value == null || value.isNull() ? fallback : value.asString();
	}

}
