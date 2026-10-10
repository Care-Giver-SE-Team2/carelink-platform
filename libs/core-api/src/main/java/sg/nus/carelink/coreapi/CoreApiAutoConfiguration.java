package sg.nus.carelink.coreapi;

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
 * A {@link CoreApi} client for a service that sets {@code carelink.core-api.base-url}. core itself
 * implements the API and does not set it, so it gets no client.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "carelink.core-api", name = "base-url")
@EnableConfigurationProperties(CoreApiProperties.class)
public class CoreApiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	CoreApi coreApi(ObjectProvider<RestClient.Builder> builders, CoreApiProperties properties) {
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
		requests.setReadTimeout(properties.readTimeout());
		RestClient.Builder builder = builders.getIfAvailable(RestClient::builder);
		return CoreApiClients.create(builder.clone().baseUrl(properties.baseUrl()).requestFactory(requests));
	}

}
