package sg.nus.carelink.openplatform.application;

import java.net.URI;
import java.net.URISyntaxException;

import sg.nus.carelink.openplatform.domain.model.InvalidField;

/**
 * Which webhook URLs a provider may give: HTTPS with a host, as the contract says, up to 2,048
 * characters. Whether its addresses are public is checked when each webhook is sent, since a name can
 * change after it is saved. A local run and the tests allow http:// too, to reach an endpoint on the
 * machine ({@code carelink.webhooks.allow-local-endpoints}).
 */
public class WebhookUrlRule {

	private final boolean allowHttp;

	public WebhookUrlRule(boolean allowHttp) {
		this.allowHttp = allowHttp;
	}

	public URI check(String field, String raw) {
		URI url;
		try {
			url = new URI(raw == null ? "" : raw.strip());
		}
		catch (URISyntaxException notAUrl) {
			throw new InvalidField(field, "Must be a URL.");
		}
		String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase();
		boolean allowed = scheme.equals("https") || (allowHttp && scheme.equals("http"));
		if (!allowed) {
			throw new InvalidField(field, "Must be HTTPS.");
		}
		if (url.getHost() == null || url.getHost().isBlank()) {
			throw new InvalidField(field, "Must name a host.");
		}
		if (url.toString().length() > 2048) {
			throw new InvalidField(field, "Must be at most 2,048 characters.");
		}
		return url;
	}
}
