package sg.nus.carelink.openplatform.infrastructure.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import sg.nus.carelink.openplatform.application.ProviderIdentities;
import sg.nus.carelink.openplatform.domain.model.InvalidField;
import sg.nus.carelink.platform.security.OpenPlatformTokens;

/**
 * Who may call the Open Platform, kept by notification itself (docs/section5/decisions.md, D15): each
 * provider's portal user, with a bcrypt hash of the password typed when applying, and the client its server
 * gets tokens with, with a SHA-256 hash of its random secret. It issues the access tokens the services check:
 * RS256, signed with {@link TokenSigningKey}, with the claims {@code OpenPlatformTokens} reads
 * ({@code token_use}, {@code client_id}, {@code username} for a portal user, {@code scope} for a server).
 *
 * <p>Five wrong passwords in a row lock a portal user for 15 minutes, against guessing. Client secrets are 51
 * random characters, which cannot be guessed, so the token endpoint does not lock.
 */
public class JdbcProviderIdentities implements ProviderIdentities {

	static final Duration TOKEN_LIFETIME = Duration.ofMinutes(60);

	static final int MIN_PASSWORD_LENGTH = 8;

	static final int MAX_FAILED_SIGN_INS = 5;

	static final Duration LOCK = Duration.ofMinutes(15);

	/** Where a provider's server asks for tokens, on CareLink's public address. */
	static final String TOKEN_PATH = "/api/open/v1/oauth2/token";

	/** The Partner Portal, which a portal user's token names as its client. */
	static final String PORTAL_CLIENT_ID = "partner-portal";

	private static final Set<String> SCOPES = Set.of(OpenPlatformTokens.JOBS_READ, OpenPlatformTokens.JOBS_WRITE);

	private static final String LOWER_AND_DIGITS = "abcdefghijklmnopqrstuvwxyz0123456789";

	private static final String LETTERS_AND_DIGITS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + LOWER_AND_DIGITS;

	private final JdbcTemplate jdbc;
	private final Clock clock;
	private final String issuer;
	private final RSAKey key;
	private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
	private final SecureRandom random = new SecureRandom();

	/** @param issuer the tokens' {@code iss}, which the services check ({@code carelink.open-platform.issuer}) */
	public JdbcProviderIdentities(JdbcTemplate jdbc, Clock clock, String issuer, RSAKey key) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.issuer = issuer;
		this.key = key;
	}

	@Override
	public String createPortalUser(String email, String password) {
		if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
			throw new InvalidField("password", "Must be at least " + MIN_PASSWORD_LENGTH + " characters.");
		}
		Timestamp now = Timestamp.from(clock.instant());
		String hash = passwords.encode(password);
		Optional<String> existing = subjectOf(email);
		if (existing.isPresent()) {
			jdbc.update("""
					UPDATE provider_portal_user
					SET password_hash = ?, failed_sign_ins = 0, locked_until = NULL, updated_at = ?
					WHERE subject = ?""", hash, now, existing.get());
			return existing.get();
		}
		String subject = UUID.randomUUID().toString();
		jdbc.update("""
				INSERT INTO provider_portal_user (subject, username, password_hash, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?)""", subject, email, hash, now, now);
		return subject;
	}

	@Override
	public ClientCredentials createClient(long providerId, String organisationName) {
		String clientId = randomOf(LOWER_AND_DIGITS, 26);
		String secret = randomOf(LETTERS_AND_DIGITS, 51);
		jdbc.update("""
				INSERT INTO provider_client (client_id, secret_hash, scopes, created_at)
				VALUES (?, ?, ?, ?)""", clientId, sha256(secret), String.join(" ", SCOPES),
				Timestamp.from(clock.instant()));
		return new ClientCredentials(clientId, secret);
	}

	@Override
	public ClientCredentials replaceClient(String oldClientId, long providerId, String organisationName) {
		ClientCredentials replacement = createClient(providerId, organisationName);
		jdbc.update("DELETE FROM provider_client WHERE client_id = ?", oldClientId);
		return replacement;
	}

	@Override
	public String tokenUrl() {
		return TOKEN_PATH;
	}

	/**
	 * A portal user's access token.
	 *
	 * @return empty for an unknown email or a wrong password
	 * @throws SignInLocked while the user is locked, whatever the password
	 */
	Optional<String> signIn(String email, String password) {
		Optional<PortalUser> found = jdbc.query("""
				SELECT subject, password_hash, locked_until FROM provider_portal_user WHERE username = ?""",
				(row, rowNum) -> new PortalUser(row.getString("subject"), row.getString("password_hash"),
						Optional.ofNullable(row.getTimestamp("locked_until")).map(Timestamp::toInstant).orElse(null)),
				email).stream().findFirst();
		if (found.isEmpty()) {
			return Optional.empty();
		}
		PortalUser user = found.get();
		Instant now = clock.instant();
		if (user.lockedUntil() != null && now.isBefore(user.lockedUntil())) {
			throw new SignInLocked();
		}
		if (password == null || !passwords.matches(password, user.passwordHash())) {
			// The fifth wrong password in a row locks the user and starts the count again
			jdbc.update("""
					UPDATE provider_portal_user
					SET locked_until = IF(failed_sign_ins + 1 >= ?, ?, NULL),
					    failed_sign_ins = IF(failed_sign_ins + 1 >= ?, 0, failed_sign_ins + 1),
					    updated_at = ?
					WHERE subject = ?""", MAX_FAILED_SIGN_INS, Timestamp.from(now.plus(LOCK)), MAX_FAILED_SIGN_INS,
					Timestamp.from(now), user.subject());
			return Optional.empty();
		}
		jdbc.update("""
				UPDATE provider_portal_user SET failed_sign_ins = 0, locked_until = NULL, updated_at = ?
				WHERE subject = ? AND (failed_sign_ins > 0 OR locked_until IS NOT NULL)""", Timestamp.from(now),
				user.subject());
		return Optional.of(token(new JWTClaimsSet.Builder()
				.subject(user.subject())
				.claim("username", email)
				.claim("client_id", PORTAL_CLIENT_ID)));
	}

	/**
	 * A provider server's access token, for the client-credentials grant: the scopes asked for, or every scope
	 * the client has when none were.
	 *
	 * @return empty for an unknown client or a wrong secret
	 * @throws IllegalArgumentException for a scope the client does not have ({@code invalid_scope})
	 */
	Optional<String> clientToken(String clientId, String clientSecret, String requestedScope) {
		Optional<String> scopes = jdbc.query("SELECT secret_hash, scopes FROM provider_client WHERE client_id = ?",
				(row, rowNum) -> Map.entry(row.getString("secret_hash"), row.getString("scopes")), clientId).stream()
				.filter(client -> clientSecret != null && MessageDigest.isEqual(
						client.getKey().getBytes(StandardCharsets.US_ASCII),
						sha256(clientSecret).getBytes(StandardCharsets.US_ASCII)))
				.map(Map.Entry::getValue)
				.findFirst();
		if (scopes.isEmpty()) {
			return Optional.empty();
		}
		Set<String> allowed = Set.of(scopes.get().split(" "));
		Set<String> granted = requestedScope == null || requestedScope.isBlank() ? allowed
				: Arrays.stream(requestedScope.strip().split("\\s+")).collect(Collectors.toCollection(LinkedHashSet::new));
		if (!allowed.containsAll(granted)) {
			throw new IllegalArgumentException("invalid_scope");
		}
		return Optional.of(token(new JWTClaimsSet.Builder()
				.subject(clientId)
				.claim("client_id", clientId)
				.claim("scope", String.join(" ", granted))));
	}

	/** The public keys tokens are signed with, which report fetches to check them. */
	Map<String, Object> jwks() {
		return new JWKSet(key.toPublicJWK()).toJSONObject();
	}

	/** The key a decoder in this service checks tokens with, without fetching {@link #jwks}. */
	public RSAPublicKey publicKey() {
		try {
			return key.toRSAPublicKey();
		}
		catch (JOSEException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	long lifetimeSeconds() {
		return TOKEN_LIFETIME.toSeconds();
	}

	private String token(JWTClaimsSet.Builder claims) {
		Instant now = clock.instant();
		claims.issuer(issuer)
				.claim("token_use", "access")
				.jwtID(UUID.randomUUID().toString())
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plus(TOKEN_LIFETIME)));
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
				claims.build());
		try {
			jwt.sign(new RSASSASigner((RSAPrivateKey) key.toPrivateKey()));
		}
		catch (JOSEException e) {
			throw new IllegalStateException("Cannot sign a token", e);
		}
		return jwt.serialize();
	}

	private Optional<String> subjectOf(String email) {
		return jdbc.queryForList("SELECT subject FROM provider_portal_user WHERE username = ?", String.class, email)
				.stream().findFirst();
	}

	private String randomOf(String alphabet, int length) {
		StringBuilder text = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			text.append(alphabet.charAt(random.nextInt(alphabet.length())));
		}
		return text.toString();
	}

	private static String sha256(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	private record PortalUser(String subject, String passwordHash, Instant lockedUntil) {
	}

	/** Too many wrong passwords in a row: no sign-in until the lock ends. */
	static class SignInLocked extends RuntimeException {

		SignInLocked() {
			super("Too many wrong passwords. Try again in " + LOCK.toMinutes() + " minutes.");
		}
	}
}
