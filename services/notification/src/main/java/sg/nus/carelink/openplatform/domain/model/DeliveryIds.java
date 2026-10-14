package sg.nus.carelink.openplatform.domain.model;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * Delivery IDs: {@code dlv_} and 16 random letters and digits, about 95 bits, so a provider can
 * keep them as its deduplication key without CareLink ever reusing one.
 */
public final class DeliveryIds {

	private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

	private static final int LENGTH = 16;

	private static final RandomGenerator RANDOM = new SecureRandom();

	private DeliveryIds() {
	}

	public static String next() {
		return next(RANDOM);
	}

	static String next(RandomGenerator random) {
		StringBuilder id = new StringBuilder("dlv_");
		for (int i = 0; i < LENGTH; i++) {
			id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return id.toString();
	}
}
