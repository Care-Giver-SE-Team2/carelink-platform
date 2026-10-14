package sg.nus.carelink.openplatform.domain.model;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * What a provider fills in to apply to the Open Platform (docs/api/open-platform.yaml,
 * {@code ApplicationDetails}), without the spaces typed around it. The contact email is also the
 * provider's portal sign-in, so it is kept in lower case: one address, one account, however it was typed.
 *
 * <p>Must not import JPA or Spring Data; ArchUnit rejects the build if it does.
 *
 * @param services names from report's value-added catalogue, each once, in the order given
 * @param webhookUrl where CareLink sends the provider's webhooks; HTTPS
 */
public record ApplicationDetails(
		String organisationName,
		String contactName,
		String contactEmail,
		String contactPhone,
		List<String> services,
		URI webhookUrl) {

	public ApplicationDetails {
		organisationName = Objects.requireNonNull(organisationName, "organisationName").strip();
		contactName = Objects.requireNonNull(contactName, "contactName").strip();
		contactEmail = normaliseEmail(contactEmail);
		contactPhone = Objects.requireNonNull(contactPhone, "contactPhone").strip();
		services = List.copyOf(services.stream().map(String::strip).distinct().toList());
		Objects.requireNonNull(webhookUrl, "webhookUrl");
		if (services.isEmpty()) {
			throw new IllegalArgumentException("A provider offers at least one service");
		}
	}

	/** An email as accounts are keyed by it: trimmed, lower case. */
	public static String normaliseEmail(String email) {
		return Objects.requireNonNull(email, "contactEmail").strip().toLowerCase(Locale.ROOT);
	}
}
