package sg.nus.carelink.openplatform.application;

import java.security.SecureRandom;

/**
 * Webhook signing secrets: {@code whsec_} and 32 random letters and digits, about 190 bits, as
 * docs/api/webhooks.md shows them. Client secrets are made with the client ({@code ProviderIdentities}).
 */
final class WebhookSecretGenerator {

	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

	private static final SecureRandom RANDOM = new SecureRandom();

	private WebhookSecretGenerator() {
	}

	static String next() {
		StringBuilder secret = new StringBuilder("whsec_");
		for (int i = 0; i < 32; i++) {
			secret.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return secret.toString();
	}
}
