package sg.nus.carelink.coreapi;

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
 * Builds a {@link CoreApi} client on a {@link RestClient.Builder}, with core's error responses
 * turned back into the exceptions the calls raised in-process.
 */
public final class CoreApiClients {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private CoreApiClients() {
	}

	/** The builder carries the base URL, timeouts and anything else the caller needs. */
	public static CoreApi create(RestClient.Builder builder) {
		RestClient client = builder.defaultStatusHandler(HttpStatusCode::isError, CoreApiClients::raise).build();
		return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build().createClient(CoreApi.class);
	}

	private static void raise(HttpRequest request, ClientHttpResponse response) throws IOException {
		byte[] body = response.getBody().readAllBytes();
		JsonNode problem = problem(body);
		String detail = text(problem, "detail", request.getMethod() + " " + request.getURI().getPath());
		switch (response.getStatusCode().value()) {
			case 403 -> throw new AccessDeniedException(detail);
			case 404 -> throw new CoreNotFound(detail);
			case 409 -> throw new CoreRuleViolation(text(problem, "code", "UNKNOWN"), detail);
			default -> throw new RestClientResponseException(
					"core answered " + response.getStatusCode().value() + " to " + request.getMethod() + " "
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
