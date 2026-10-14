package sg.nus.carelink.openplatform.application;

/**
 * How providers' webhook signing secrets are kept: never as themselves. {@link #store} seals a
 * secret into the value the database keeps, and {@link #reveal} turns that value back into the
 * secret, only to sign. A secret is never changed in place; rotating stores a new one.
 */
public interface SigningSecrets {

	/** Seals {@code secret} for {@code providerId}; the database keeps what this returns. */
	String store(long providerId, String secret);

	/** The secret {@code sealed} holds. */
	String reveal(String sealed);
}
