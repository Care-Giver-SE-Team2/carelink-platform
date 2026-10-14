package sg.nus.carelink.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the Open Platform's bearer tokens come from, from {@code carelink.open-platform}. Setting
 * {@code jwk-set-uri} is what turns the check of {@code /api/open/**} on (see
 * {@link OpenPlatformSecurityConfiguration}); a service without it serves no {@code /api/open} path.
 *
 * @param jwkSetUri the keys the tokens are signed with: notification's
 *        {@code /api/open/v1/.well-known/jwks.json}, which issues them
 * @param issuer the tokens' {@code iss}, checked when set
 */
@ConfigurationProperties(prefix = "carelink.open-platform")
public record OpenPlatformProperties(String jwkSetUri, String issuer) {
}
