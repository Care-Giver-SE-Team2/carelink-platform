package sg.nus.carelink.events;

import java.time.Duration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * The SNS and SQS clients the relay and the consumer own. Credentials come from the default chain:
 * the pod's IAM role in the cluster, {@code AWS_ACCESS_KEY_ID} and {@code AWS_SECRET_ACCESS_KEY}
 * for LocalStack.
 */
final class AwsClients {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

	private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(10);

	/** A long poll holds the connection open for up to receiveWait; the read timeout must outlast it. */
	private static final Duration RECEIVE_MARGIN = Duration.ofSeconds(10);

	private AwsClients() {
	}

	static SnsClient sns(EventsProperties properties) {
		var builder = SnsClient.builder()
				.region(Region.of(properties.region()))
				.credentialsProvider(DefaultCredentialsProvider.builder().build())
				.httpClientBuilder(UrlConnectionHttpClient.builder()
						.connectionTimeout(CONNECT_TIMEOUT)
						.socketTimeout(PUBLISH_TIMEOUT));
		if (properties.endpoint() != null) {
			builder.endpointOverride(properties.endpoint());
		}
		return builder.build();
	}

	static SqsClient sqs(EventsProperties properties) {
		var builder = SqsClient.builder()
				.region(Region.of(properties.region()))
				.credentialsProvider(DefaultCredentialsProvider.builder().build())
				.httpClientBuilder(UrlConnectionHttpClient.builder()
						.connectionTimeout(CONNECT_TIMEOUT)
						.socketTimeout(properties.receiveWait().plus(RECEIVE_MARGIN)));
		if (properties.endpoint() != null) {
			builder.endpointOverride(properties.endpoint());
		}
		return builder.build();
	}

}
