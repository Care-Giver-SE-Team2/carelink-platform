package sg.nus.carelink.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole path against MySQL and LocalStack: an event written in a transaction is published by
 * the relay through SNS to an SQS queue and handled once by this service's handler; a rolled-back
 * transaction publishes nothing; a second delivery of a message is ignored; a failed handling is
 * delivered again. The tables come from the library's own platform migration.
 */
@SpringBootTest(classes = EventsIT.Service.class, properties = {
		"spring.flyway.locations=classpath:db/platform",
		"carelink.events.service=visit",
		"carelink.events.relay-interval=200ms",
		"carelink.events.receive-wait=1s"})
class EventsIT {

	/** The topic and the queues come from the script docker-compose.yml gives LocalStack. */
	private static final LocalStackContainer AWS = new LocalStackContainer("localstack/localstack:4.14.0")
			.withServices("sns", "sqs")
			.withCopyFileToContainer(MountableFile.forHostPath("../../scripts/localstack/events.sh", 0755),
					"/etc/localstack/init/ready.d/events.sh");

	private static final String REGION = "ap-southeast-1";

	private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

	private static final String TOPIC_ARN;

	private static final String QUEUE_URL;

	static {
		AWS.start();
		MYSQL.start();
		// For the library's clients, which use the default credentials chain
		System.setProperty("aws.accessKeyId", AWS.getAccessKey());
		System.setProperty("aws.secretAccessKey", AWS.getSecretKey());
		TOPIC_ARN = "arn:aws:sns:" + REGION + ":000000000000:carelink-events";
		awaitInitScript();
		try (SqsClient sqs = sqs()) {
			QUEUE_URL = sqs.getQueueUrl(queue -> queue.queueName("carelink-visit")).queueUrl();
			// A failed handling comes back after one second instead of thirty
			sqs.setQueueAttributes(queue -> queue.queueUrl(QUEUE_URL)
					.attributes(Map.of(QueueAttributeName.VISIBILITY_TIMEOUT, "1")));
		}
	}

	/**
	 * The script's last step subscribes the fourth queue, so the script is done when the topic has
	 * four subscriptions. Polled in this thread: this runs in the class's static initializer, and a
	 * condition run on Awaitility's own thread would wait for that initializer to finish.
	 */
	private static void awaitInitScript() {
		try (SnsClient sns = SnsClient.builder().endpointOverride(AWS.getEndpoint()).region(Region.of(REGION))
				.credentialsProvider(credentials()).build()) {
			await().pollInSameThread().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(500)).ignoreExceptions()
					.until(() -> sns.listSubscriptionsByTopic(topic -> topic.topicArn(TOPIC_ARN)).subscriptions().size() == 4);
		}
		catch (ConditionTimeoutException notFinished) {
			throw new IllegalStateException(
					"LocalStack's init script did not finish. LocalStack's log:%n%s".formatted(AWS.getLogs()), notFinished);
		}
	}

	@Autowired
	private Events events;

	private TransactionTemplate transactions;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JsonMapper json;

	@Autowired
	private Received received;

	@Autowired
	void transactions(PlatformTransactionManager transactionManager) {
		this.transactions = new TransactionTemplate(transactionManager);
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("carelink.events.endpoint", () -> AWS.getEndpoint().toString());
		registry.add("carelink.events.region", () -> REGION);
		registry.add("carelink.events.topic-arn", () -> TOPIC_ARN);
		registry.add("carelink.events.queue-url", () -> QUEUE_URL);
	}

	@Test
	void anEventPublishedInATransactionReachesTheHandlerOnce() {
		transactions.executeWithoutResult(status -> events.publish("VisitMissed", new VisitMissed(101L, 3L)));

		EventMetadata metadata = awaitHandled(101L);
		assertThat(metadata.type()).isEqualTo("VisitMissed");
		assertThat(metadata.source()).isEqualTo("visit");
		assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM outbox_event WHERE event_id = ?",
				Boolean.class, metadata.id())).isTrue();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumed_message WHERE consumer = 'visit' AND message_id = ?",
				Integer.class, metadata.id())).isEqualTo(1);
	}

	@Test
	void aRolledBackTransactionPublishesNothing() {
		transactions.executeWithoutResult(status -> {
			events.publish("VisitMissed", new VisitMissed(102L, 3L));
			status.setRollbackOnly();
		});
		transactions.executeWithoutResult(status -> events.publish("VisitMissed", new VisitMissed(103L, 3L)));

		awaitHandled(103L);
		assertThat(received.visits).extracting(VisitMissed::visitId).doesNotContain(102L);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE payload->>'$.visitId' = '102'",
				Integer.class)).isZero();
	}

	@Test
	void anEventOutsideATransactionIsRefused() {
		assertThatThrownBy(() -> events.publish("VisitMissed", new VisitMissed(104L, 3L)))
				.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void aSecondDeliveryOfAMessageIsIgnored() {
		transactions.executeWithoutResult(status -> events.publish("VisitMissed", new VisitMissed(105L, 3L)));
		EventMetadata metadata = awaitHandled(105L);

		String again = new Envelope(metadata.id(), metadata.type(), metadata.source(), metadata.occurredAt(),
				json.valueToTree(new VisitMissed(105L, 3L))).toJson(json);
		try (SqsClient sqs = sqs()) {
			sqs.sendMessage(message -> message.queueUrl(QUEUE_URL).messageBody(again));
			await().atMost(Duration.ofSeconds(30)).until(() -> queueIsEmpty(sqs));
		}

		assertThat(received.visits).extracting(VisitMissed::visitId).containsOnlyOnce(105L);
	}

	@Test
	void aFailedHandlingIsDeliveredAgain() {
		transactions.executeWithoutResult(status -> events.publish("Flaky", new Flaky("first fails")));

		await().atMost(Duration.ofSeconds(30)).until(() -> received.flakyHandled.get() == 1);
		assertThat(received.flakyAttempts.get()).isEqualTo(2);
	}

	private EventMetadata awaitHandled(long visitId) {
		await().atMost(Duration.ofSeconds(30))
				.until(() -> received.visits.stream().anyMatch(visit -> visit.visitId() == visitId));
		int index = received.visits.stream().map(VisitMissed::visitId).toList().indexOf(visitId);
		return received.metadata.get(index);
	}

	private static boolean queueIsEmpty(SqsClient sqs) {
		Map<QueueAttributeName, String> counts = sqs.getQueueAttributes(attributes -> attributes.queueUrl(QUEUE_URL)
				.attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
						QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)).attributes();
		return "0".equals(counts.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
				&& "0".equals(counts.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
	}

	private static SqsClient sqs() {
		return SqsClient.builder().endpointOverride(AWS.getEndpoint()).region(Region.of(REGION))
				.credentialsProvider(credentials()).build();
	}

	private static StaticCredentialsProvider credentials() {
		return StaticCredentialsProvider.create(AwsBasicCredentials.create(AWS.getAccessKey(), AWS.getSecretKey()));
	}

	record VisitMissed(Long visitId, Long elderId) {
	}

	record Flaky(String note) {
	}

	/** What the handlers saw, in the order they saw it. */
	static class Received {

		final List<VisitMissed> visits = new CopyOnWriteArrayList<>();

		final List<EventMetadata> metadata = new CopyOnWriteArrayList<>();

		final AtomicInteger flakyAttempts = new AtomicInteger();

		final AtomicInteger flakyHandled = new AtomicInteger();

	}

	static class VisitMissedHandler implements EventHandler<VisitMissed> {

		private final Received received;

		VisitMissedHandler(Received received) {
			this.received = received;
		}

		@Override
		public String type() {
			return "VisitMissed";
		}

		@Override
		public Class<VisitMissed> payloadType() {
			return VisitMissed.class;
		}

		@Override
		public void handle(VisitMissed payload, EventMetadata metadata) {
			received.visits.add(payload);
			received.metadata.add(metadata);
		}

	}

	static class FlakyHandler implements EventHandler<Flaky> {

		private final Received received;

		FlakyHandler(Received received) {
			this.received = received;
		}

		@Override
		public String type() {
			return "Flaky";
		}

		@Override
		public Class<Flaky> payloadType() {
			return Flaky.class;
		}

		@Override
		public void handle(Flaky payload, EventMetadata metadata) {
			if (received.flakyAttempts.incrementAndGet() == 1) {
				throw new IllegalStateException("The first delivery fails");
			}
			received.flakyHandled.incrementAndGet();
		}

	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import({Received.class, VisitMissedHandler.class, FlakyHandler.class})
	static class Service {
	}

}
