package sg.nus.carelink.coreapi;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where core is and how long a call may take.
 *
 * @param baseUrl core inside the cluster, {@code http://core}; {@code http://localhost:8080} for a
 * service run on a laptop
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for core's answer
 */
@ConfigurationProperties("carelink.core-api")
public record CoreApiProperties(String baseUrl, @DefaultValue("2s") Duration connectTimeout,
		@DefaultValue("5s") Duration readTimeout) {
}
