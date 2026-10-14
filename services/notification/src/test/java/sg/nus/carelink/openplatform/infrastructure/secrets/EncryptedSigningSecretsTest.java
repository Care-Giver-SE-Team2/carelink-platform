package sg.nus.carelink.openplatform.infrastructure.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * Signing secrets sealed with AES-256-GCM: what the database keeps opens back into the secret with
 * the same key, and with nothing else; a sealed value changed in the database is refused, never
 * turned into a wrong secret; and the service will not start with a missing or malformed key.
 */
class EncryptedSigningSecretsTest {

	private static final String KEY = key('k');

	private static final String OTHER_KEY = key('o');

	private final EncryptedSigningSecrets secrets = new EncryptedSigningSecrets(KEY);

	@Test
	void aSealedSecretOpensBackIntoItselfAndDoesNotShowIt() {
		String sealed = secrets.store(509, "whsec_abc123");

		assertThat(sealed).startsWith("v1:").doesNotContain("whsec_abc123");
		assertThat(secrets.reveal(sealed)).isEqualTo("whsec_abc123");
		assertThat(new EncryptedSigningSecrets(KEY).reveal(sealed)).as("by another process with the key")
				.isEqualTo("whsec_abc123");
	}

	@Test
	void theSameSecretSealsDifferentlyEachTime() {
		assertThat(secrets.store(509, "whsec_abc123")).isNotEqualTo(secrets.store(509, "whsec_abc123"));
	}

	@Test
	void anotherKeyCannotOpenIt() {
		String sealed = secrets.store(509, "whsec_abc123");

		assertThatThrownBy(() -> new EncryptedSigningSecrets(OTHER_KEY).reveal(sealed))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("could not be opened");
	}

	@Test
	void aSealedValueChangedInTheDatabaseIsRefused() {
		String sealed = secrets.store(509, "whsec_abc123");
		byte[] bytes = Base64.getDecoder().decode(sealed.substring(3));
		bytes[bytes.length - 1] ^= 1;
		String tampered = "v1:" + Base64.getEncoder().encodeToString(bytes);

		assertThatThrownBy(() -> secrets.reveal(tampered)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void somethingThatWasNeverSealedIsRefused() {
		assertThatThrownBy(() -> secrets.reveal("whsec_plain")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> secrets.reveal("v1:not base64!")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> secrets.reveal("v1:AAAA")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> secrets.reveal(null)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void theServiceDoesNotStartWithoutAProperKey() {
		assertThatThrownBy(() -> new EncryptedSigningSecrets(null)).hasMessageContaining("is not set");
		assertThatThrownBy(() -> new EncryptedSigningSecrets(" ")).hasMessageContaining("is not set");
		assertThatThrownBy(() -> new EncryptedSigningSecrets("not base64!")).hasMessageContaining("not base64");
		assertThatThrownBy(() -> new EncryptedSigningSecrets(Base64.getEncoder().encodeToString(new byte[16])))
				.hasMessageContaining("must be 32 bytes");
	}

	private static String key(char fill) {
		return Base64.getEncoder().encodeToString(String.valueOf(fill).repeat(32).getBytes());
	}
}
