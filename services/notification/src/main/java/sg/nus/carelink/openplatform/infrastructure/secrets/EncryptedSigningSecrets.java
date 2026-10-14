package sg.nus.carelink.openplatform.infrastructure.secrets;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import sg.nus.carelink.openplatform.application.SigningSecrets;

/**
 * Signing secrets sealed with AES-256-GCM, so the database keeps them encrypted and a copy of it
 * (a backup, a dump) cannot sign a webhook. The key is the service's setting
 * {@code carelink.webhooks.encryption-key}, kept where the database password is: {@code .env}
 * locally, the service's Kubernetes Secret in the cloud. It is never in the database or in git.
 *
 * <p>A sealed secret is {@code v1:} and the base64 of a random 12-byte nonce followed by the
 * ciphertext and its 16-byte tag. GCM checks the tag on the way back, so a sealed value changed in
 * the database, or opened with another key, is refused rather than turned into a wrong secret.
 * The {@code v1} leaves room for a second key, should the key ever have to change.
 */
public class EncryptedSigningSecrets implements SigningSecrets {

	private static final String PREFIX = "v1:";

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private static final int KEY_BYTES = 32;

	private static final int NONCE_BYTES = 12;

	private static final int TAG_BITS = 128;

	private final SecretKey key;
	private final SecureRandom random = new SecureRandom();

	/** @param base64Key 32 random bytes, base64-encoded: {@code openssl rand -base64 32} */
	public EncryptedSigningSecrets(String base64Key) {
		if (base64Key == null || base64Key.isBlank()) {
			throw new IllegalStateException(
					"carelink.webhooks.encryption-key is not set: 32 random bytes, base64 (openssl rand -base64 32)");
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(base64Key.trim());
		}
		catch (IllegalArgumentException notBase64) {
			throw new IllegalStateException("carelink.webhooks.encryption-key is not base64", notBase64);
		}
		if (bytes.length != KEY_BYTES) {
			throw new IllegalStateException("carelink.webhooks.encryption-key must be 32 bytes; it is " + bytes.length);
		}
		this.key = new SecretKeySpec(bytes, "AES");
	}

	@Override
	public String store(long providerId, String secret) {
		byte[] nonce = new byte[NONCE_BYTES];
		random.nextBytes(nonce);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
			byte[] sealed = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
			return PREFIX + Base64.getEncoder().encodeToString(
					ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array());
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Cannot seal a signing secret", e);
		}
	}

	@Override
	public String reveal(String sealed) {
		if (sealed == null || !sealed.startsWith(PREFIX)) {
			throw new IllegalStateException("Not a sealed signing secret");
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(sealed.substring(PREFIX.length()));
		}
		catch (IllegalArgumentException notBase64) {
			throw new IllegalStateException("Not a sealed signing secret", notBase64);
		}
		if (bytes.length <= NONCE_BYTES) {
			throw new IllegalStateException("Not a sealed signing secret");
		}
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES));
			return new String(cipher.doFinal(bytes, NONCE_BYTES, bytes.length - NONCE_BYTES), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException e) {
			// Changed in the database, or sealed with another key: never a wrong secret
			throw new IllegalStateException("A signing secret could not be opened", e);
		}
	}
}
