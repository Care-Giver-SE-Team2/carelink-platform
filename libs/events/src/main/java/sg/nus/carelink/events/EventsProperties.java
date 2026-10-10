package sg.nus.carelink.events;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code carelink.events}. Publishing to the outbox needs none of them; the relay
 * starts when {@code topic-arn} is set and the consumer when {@code queue-url} is set.
 *
 * @param service this service's name: the source of the events it publishes and the consumer name
 *     under which it records the events it handled. Defaults to {@code spring.application.name}
 * @param topicArn the SNS topic the relay publishes the outbox to
 * @param queueUrl this service's SQS queue, subscribed to the topic
 * @param endpoint where SNS and SQS are reached, for LocalStack; empty in AWS
 * @param region the AWS region
 * @param relayInterval how long the relay waits after finding the outbox empty
 * @param relayBatchSize how many events the relay takes in one transaction
 * @param receiveWait how long one receive waits for a message (SQS long polling, at most 20 s)
 */
@ConfigurationProperties("carelink.events")
public record EventsProperties(
		String service,
		String topicArn,
		String queueUrl,
		URI endpoint,
		@DefaultValue("ap-southeast-1") String region,
		@DefaultValue("1s") Duration relayInterval,
		@DefaultValue("50") int relayBatchSize,
		@DefaultValue("10s") Duration receiveWait) {
}
