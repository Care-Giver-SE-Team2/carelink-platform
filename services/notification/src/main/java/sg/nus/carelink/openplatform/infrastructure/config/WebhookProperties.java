package sg.nus.carelink.openplatform.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How notification sends webhooks, from {@code carelink.webhooks}.
 *
 * @param allowLocalEndpoints send to http:// and to private and loopback addresses too: for a local
 *        run and the tests only, never in the cloud (see {@code PublicAddresses})
 * @param connectTimeout how long to wait for the endpoint's connection
 * @param requestTimeout how long an attempt may take before it counts as unanswered: 10 seconds,
 *        as docs/api/webhooks.md promises
 * @param encryptionKey the key signing secrets are encrypted with in the database: 32 random bytes,
 *        base64. No default: the service does not start without it (see {@code EncryptedSigningSecrets})
 */
@ConfigurationProperties(prefix = "carelink.webhooks")
public record WebhookProperties(
		@DefaultValue("false") boolean allowLocalEndpoints,
		@DefaultValue("5s") Duration connectTimeout,
		@DefaultValue("10s") Duration requestTimeout,
		String encryptionKey) {
}
