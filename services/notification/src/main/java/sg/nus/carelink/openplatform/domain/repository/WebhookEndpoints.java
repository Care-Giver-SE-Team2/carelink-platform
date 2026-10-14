package sg.nus.carelink.openplatform.domain.repository;

import java.util.Optional;

import sg.nus.carelink.openplatform.domain.model.WebhookEndpoint;

/** Each provider's webhook endpoint, by the provider's id. */
public interface WebhookEndpoints {

	Optional<WebhookEndpoint> find(long providerId);

	/** Creates the provider's endpoint or replaces it. */
	void save(WebhookEndpoint endpoint);
}
