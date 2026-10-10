package sg.nus.carelink.visitapi;

import java.net.http.HttpClient;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * A {@link VisitApi} client for a caller that sets {@code carelink.visit-api.base-url}. The service
 * that implements the API does not set it, so it gets no client.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "carelink.visit-api", name = "base-url")
@EnableConfigurationProperties(VisitApiProperties.class)
public class VisitApiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	VisitApi visitApi(ObjectProvider<RestClient.Builder> builders, VisitApiProperties properties) {
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
		requests.setReadTimeout(properties.readTimeout());
		RestClient.Builder builder = builders.getIfAvailable(RestClient::builder);
		return VisitApiClients.create(builder.clone().baseUrl(properties.baseUrl()).requestFactory(requests));
	}

}
