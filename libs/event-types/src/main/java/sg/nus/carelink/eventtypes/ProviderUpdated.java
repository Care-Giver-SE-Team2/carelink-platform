package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * An approved Open Platform provider was added or changed: approved, suspended or reinstated, its
 * client credentials rotated, or the services it offers changed. It carries the provider's whole state
 * after the change. Published by notification (openplatform) in the transaction that saves it; handled
 * by report, which keeps whom it may dispatch jobs to and whose token is whose. A pending or rejected
 * application sends nothing.
 *
 * @param clientId the provider's OAuth2 client, which the job API's token names ({@code client_id}); it
 *        may change when the provider rotates its secret
 * @param status {@code ACTIVE} or {@code SUSPENDED}; a suspended provider is dispatched nothing, and its
 *        token is refused
 * @param services names from report's value-added catalogue
 * @param version increases with every change to the provider: a handler ignores a version older than
 *        the one it has
 */
public record ProviderUpdated(Long providerId, String clientId, String organisationName, String status,
		List<String> services, Integer version, OffsetDateTime changedAt) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "ProviderUpdated";

	public ProviderUpdated {
		services = services == null ? List.of() : List.copyOf(services);
	}

}
