package sg.nus.carelink.visitapi;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where visit is and how long a call may take.
 *
 * @param baseUrl visit inside the cluster, {@code http://visit}; while visit is still inside core,
 * core's address
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout how long to wait for visit's answer
 */
@ConfigurationProperties("carelink.visit-api")
public record VisitApiProperties(String baseUrl, @DefaultValue("2s") Duration connectTimeout,
		@DefaultValue("5s") Duration readTimeout) {
}
