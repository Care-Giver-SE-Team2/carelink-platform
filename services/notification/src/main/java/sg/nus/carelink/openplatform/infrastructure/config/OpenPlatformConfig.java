package sg.nus.carelink.openplatform.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import sg.nus.carelink.openplatform.application.SigningSecrets;
import sg.nus.carelink.openplatform.infrastructure.secrets.EncryptedSigningSecrets;

/**
 * The Open Platform module's Spring configuration: its settings, the scheduler its webhook
 * attempts need, and how signing secrets are kept: encrypted in notification's own database, with
 * the key from {@code carelink.webhooks.encryption-key}. A missing or malformed key stops the
 * service at start-up, not at the first webhook.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(WebhookProperties.class)
class OpenPlatformConfig {

	@Bean
	SigningSecrets signingSecrets(WebhookProperties properties) {
		return new EncryptedSigningSecrets(properties.encryptionKey());
	}
}
