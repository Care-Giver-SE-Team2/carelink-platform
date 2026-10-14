package sg.nus.carelink.openplatform.application;

/**
 * Where the Open Platform's identities live: in notification's own database, which issues their tokens
 * (docs/section5/decisions.md, D15). A provider has two: the portal user its people sign in to the
 * Partner Portal as, with the password typed when applying, and the app client its server gets tokens
 * with. Neither the password nor the client secret is kept as itself, only as a hash.
 */
public interface ProviderIdentities {

	/**
	 * The portal user for {@code email}, with {@code password}: created, or, for an email that applied
	 * before and was rejected, given the new password.
	 *
	 * @return the user's {@code sub}, which its tokens carry
	 * @throws sg.nus.carelink.openplatform.domain.model.InvalidField for a password too short
	 */
	String createPortalUser(String email, String password);

	/** A new app client for the provider's server, with the job scopes and a secret. */
	ClientCredentials createClient(long providerId, String organisationName);

	/**
	 * A new secret for the provider's server. The old one stops working at once. The client is new too,
	 * so that a token got with the old secret names a client that no longer exists.
	 */
	ClientCredentials replaceClient(String oldClientId, long providerId, String organisationName);

	/** Where the provider's server asks for tokens: a path on CareLink's public address. */
	String tokenUrl();

	/** A client and its secret, as issued. The secret is shown once and is not kept by CareLink. */
	record ClientCredentials(String clientId, String clientSecret) {
	}
}
