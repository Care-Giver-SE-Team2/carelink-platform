package sg.nus.carelink.openplatform.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The {@code X-CareLink-Signature} header (docs/api/webhooks.md, section 5): HMAC-SHA256, keyed
 * with the provider's signing secret, over the timestamp, a dot and the body, as lowercase hex
 * after {@code v1=}. During a rotation's overlap the header carries one value per secret, newest
 * first, comma-separated, and a receiver accepts any of them.
 */
public final class WebhookSignature {

	private static final String ALGORITHM = "HmacSHA256";

	private WebhookSignature() {
	}

	/** The hex signature of {@code body} sent at {@code timestamp} (Unix seconds), with {@code secret}. */
	public static String sign(String secret, long timestamp, String body) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
			mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			// Every JDK has HmacSHA256, and any non-empty key is valid for it
			throw new IllegalStateException("Cannot sign a webhook", e);
		}
	}

	/** The header's value for {@code secrets}, newest first: {@code v1=<hex>[,v1=<hex>]}. */
	public static String header(List<String> secrets, long timestamp, String body) {
		if (secrets.isEmpty()) {
			throw new IllegalArgumentException("A webhook is signed with at least one secret");
		}
		return secrets.stream()
				.map(secret -> "v1=" + sign(secret, timestamp, body))
				.collect(Collectors.joining(","));
	}
}
