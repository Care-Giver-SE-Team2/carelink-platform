package sg.nus.carelink.openplatform.infrastructure.identity;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.Clock;
import java.util.UUID;

import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.jdbc.core.JdbcTemplate;

import sg.nus.carelink.openplatform.application.SigningSecrets;

/**
 * The key the Open Platform's tokens are signed with, kept in {@code provider_token_key}, encrypted as the
 * webhook signing secrets are. The first replica to start makes it; the others, and every restart, read the
 * same one, so a token signed by one replica is taken by all of them and outlives a restart.
 */
final class TokenSigningKey {

	private static final String CURRENT = "current";

	/** Not a provider's: {@link SigningSecrets#store} takes an id but seals the same way for any. */
	private static final long NO_PROVIDER = 0;

	private TokenSigningKey() {
	}

	/** The current key, made and kept first if there is none yet. */
	static RSAKey loadOrCreate(JdbcTemplate jdbc, SigningSecrets secrets, Clock clock) {
		RSAKey made = newKey();
		// Two replicas starting at once both try; one row is kept, and both read it back
		jdbc.update("""
				INSERT IGNORE INTO provider_token_key (name, key_id, private_key, created_at)
				VALUES (?, ?, ?, ?)""", CURRENT, made.getKeyID(), secrets.store(NO_PROVIDER, made.toJSONString()),
				Timestamp.from(clock.instant()));
		String sealed = jdbc.queryForObject("SELECT private_key FROM provider_token_key WHERE name = ?", String.class,
				CURRENT);
		try {
			return RSAKey.parse(secrets.reveal(sealed));
		}
		catch (ParseException e) {
			throw new IllegalStateException("The Open Platform's token key in provider_token_key is not a JWK", e);
		}
	}

	private static RSAKey newKey() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			KeyPair pair = generator.generateKeyPair();
			return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
					.privateKey((RSAPrivateKey) pair.getPrivate())
					.keyID(UUID.randomUUID().toString())
					.build();
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}
}
