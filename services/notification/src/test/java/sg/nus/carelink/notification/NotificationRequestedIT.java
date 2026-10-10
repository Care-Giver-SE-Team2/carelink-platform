package sg.nus.carelink.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.Subscription;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import sg.nus.carelink.eventtypes.NotificationRequested;
import sg.nus.carelink.testsupport.PlatformTablesForTests;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * notification takes {@code NotificationRequested} off its queue, end to end against LocalStack and
 * MySQL, with the topic, queues and subscriptions a local run gets ({@code scripts/localstack/events.sh}).
 * An event published to the topic reaches notification's queue through its filter policy and becomes
 * a PENDING message for its recipient, at the time it was requested, and the queue's subscription
 * takes no other type of event. (That a second delivery changes nothing is the library's, tested in
 * EventsIT.)
 */
@SpringBootTest(properties = "carelink.events.receive-wait=1s")
@Import(PlatformTablesForTests.class)
class NotificationRequestedIT {

	private static final LocalStackContainer AWS = new LocalStackContainer("localstack/localstack:4.14.0")
			.withServices("sns", "sqs")
			.withCopyFileToContainer(MountableFile.forHostPath("../../scripts/localstack/events.sh", 0755),
					"/etc/localstack/init/ready.d/events.sh");

	private static final String REGION = "ap-southeast-1";

	private static final String TOPIC_ARN = "arn:aws:sns:" + REGION + ":000000000000:carelink-events";

	private static final String QUEUE_URL;

	static {
		AWS.start();
		// For the library's clients, which use the default credentials chain
		System.setProperty("aws.accessKeyId", AWS.getAccessKey());
		System.setProperty("aws.secretAccessKey", AWS.getSecretKey());
		awaitInitScript();
		try (SqsClient sqs = sqs()) {
			QUEUE_URL = sqs.getQueueUrl(queue -> queue.queueName("carelink-notification")).queueUrl();
		}
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, NotificationRequestedIT.class, null, "connectionTimeZone=Asia/Singapore");
		registry.add("carelink.events.endpoint", () -> AWS.getEndpoint().toString());
		registry.add("carelink.events.region", () -> REGION);
		registry.add("carelink.events.queue-url", () -> QUEUE_URL);
	}

	@Autowired
	private JdbcTemplate jdbc;

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void aRequestedMessageIsKeptForItsRecipient() {
		String id = UUID.randomUUID().toString();
		NotificationRequested requested = new NotificationRequested(8702L, "CREDENTIAL_EXPIRED", "IN_APP",
				"Your First aid certificate expired on 3 Oct 2026", "Upload the renewed certificate.", "CREDENTIAL",
				8732L, null, OffsetDateTime.parse("2026-10-04T01:15:00+08:00"));

		publish(id, NotificationRequested.TYPE, requested);

		await().atMost(Duration.ofSeconds(30)).until(() -> messagesFor(8702L) == 1);
		Map<String, Object> kept = jdbc.queryForMap("select event_type, channel, title, resource_type, resource_id,"
				+ " status from notification where recipient_user_id = 8702");
		assertThat(kept).containsEntry("event_type", "CREDENTIAL_EXPIRED")
				.containsEntry("channel", "IN_APP")
				.containsEntry("title", "Your First aid certificate expired on 3 Oct 2026")
				.containsEntry("resource_type", "CREDENTIAL")
				.containsEntry("status", "PENDING");
		assertThat(jdbc.queryForObject("select created_at from notification where recipient_user_id = 8702",
				Timestamp.class).toLocalDateTime()).as("the time it was requested, on Singapore's clock")
				.isEqualTo(LocalDateTime.of(2026, 10, 4, 1, 15));
	}

	@Test
	void notificationsQueueTakesOnlyTheEventsItHandles() {
		try (SnsClient sns = sns()) {
			Subscription mine = sns.listSubscriptionsByTopic(topic -> topic.topicArn(TOPIC_ARN)).subscriptions().stream()
					.filter(subscription -> subscription.endpoint().endsWith(":carelink-notification"))
					.findFirst().orElseThrow();
			String policy = sns.getSubscriptionAttributes(attributes -> attributes.subscriptionArn(mine.subscriptionArn()))
					.attributes().get("FilterPolicy");

			assertThat(json.readTree(policy).get("type").valueStream().map(type -> type.asString()).toList())
					.containsExactly(NotificationRequested.TYPE);
		}
	}

	private int messagesFor(long userId) {
		return jdbc.queryForObject("select count(*) from notification where recipient_user_id = ?", Integer.class, userId);
	}

	/** Published the way the outbox relay publishes: the envelope as the message, its type as an attribute. */
	private void publish(String id, String type, Object payload) {
		ObjectNode envelope = json.createObjectNode();
		envelope.put("id", id);
		envelope.put("type", type);
		envelope.put("source", "core");
		envelope.put("occurredAt", Instant.now().toString());
		envelope.put("sequence", 1L);
		envelope.set("payload", json.valueToTree(payload));
		try (SnsClient sns = sns()) {
			sns.publish(message -> message.topicArn(TOPIC_ARN).message(json.writeValueAsString(envelope))
					.messageAttributes(Map.of("type", attribute(type), "source", attribute("core"))));
		}
	}

	private static MessageAttributeValue attribute(String value) {
		return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
	}

	/**
	 * The script's last step subscribes report's queue, the second subscription, so the script is
	 * done when the topic has two. Polled in this thread: this runs in the class's static
	 * initializer, and a condition run on Awaitility's own thread would wait for that initializer
	 * to finish.
	 */
	private static void awaitInitScript() {
		try (SnsClient sns = sns()) {
			await().pollInSameThread().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(500)).ignoreExceptions()
					.until(() -> sns.listSubscriptionsByTopic(topic -> topic.topicArn(TOPIC_ARN)).subscriptions().size() == 2);
		}
		catch (ConditionTimeoutException notFinished) {
			throw new IllegalStateException(
					"LocalStack's init script did not finish. LocalStack's log:%n%s".formatted(AWS.getLogs()), notFinished);
		}
	}

	private static SnsClient sns() {
		return SnsClient.builder().endpointOverride(AWS.getEndpoint()).region(Region.of(REGION))
				.credentialsProvider(credentials()).build();
	}

	private static SqsClient sqs() {
		return SqsClient.builder().endpointOverride(AWS.getEndpoint()).region(Region.of(REGION))
				.credentialsProvider(credentials()).build();
	}

	private static StaticCredentialsProvider credentials() {
		return StaticCredentialsProvider.create(AwsBasicCredentials.create(AWS.getAccessKey(), AWS.getSecretKey()));
	}

}
