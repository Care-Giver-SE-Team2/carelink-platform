package sg.nus.carelink.openplatform.domain.model;

/**
 * The provider was removed by CareLink: nothing it had may be used or changed, and what only a provider
 * has is refused as for a provider never approved, with its own code, 403 {@code PROVIDER_REMOVED}. Its
 * people may still sign in to the portal, to see that, and apply again.
 */
public class ProviderRemoved extends ProviderNotApproved {

	public ProviderRemoved() {
		super("PROVIDER_REMOVED", "CareLink has removed this provider. You may apply again.");
	}
}
