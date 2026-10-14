package sg.nus.carelink.openplatform.infrastructure.identity;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import sg.nus.carelink.openplatform.application.SigningSecrets;
import sg.nus.carelink.platform.security.OpenPlatformProperties;
import sg.nus.carelink.platform.security.OpenPlatformTokens;

/**
 * notification as the Open Platform's identity provider (docs/section5/decisions.md, D15), the same locally
 * and in the cloud: it needs no setting the service does not already have, as its signing key is kept in its
 * own database, encrypted with {@code carelink.webhooks.encryption-key}.
 *
 * <p>This service checks the tokens with its key directly, with the checks the platform's decoder makes,
 * instead of fetching its own key set over HTTP; report fetches the key set.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenPlatformProperties.class)
public class IdentityConfig {

	@Bean
	JdbcProviderIdentities providerIdentities(JdbcTemplate jdbc, SigningSecrets secrets, Clock clock,
			OpenPlatformProperties platform) {
		if (platform.issuer() == null || platform.issuer().isBlank()) {
			throw new IllegalStateException("carelink.open-platform.issuer is not set: the tokens need it");
		}
		return new JdbcProviderIdentities(jdbc, clock, platform.issuer(),
				TokenSigningKey.loadOrCreate(jdbc, secrets, clock));
	}

	@Bean
	JwtDecoder openPlatformJwtDecoder(JdbcProviderIdentities identities, OpenPlatformProperties platform) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(identities.publicKey()).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(platform.issuer()), OpenPlatformTokens.accessTokensOnly()));
		return decoder;
	}
}
