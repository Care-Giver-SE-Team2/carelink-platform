package sg.nus.carelink.events;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Takes events from this service's SQS queue and gives each to its {@link EventHandler}, once.
 *
 * <p>SQS delivers a message at least once. Each event is handled in a transaction that first
 * records it in {@code consumed_message}; a second delivery, to this replica or another, finds the
 * row and is skipped. The message is deleted from the queue only after that transaction commits.
 * When handling fails, the message stays: SQS delivers it again after the queue's visibility
 * timeout, and after the queue's maximum number of receives moves it to the dead-letter queue.
 */
final class QueueConsumer implements SmartLifecycle, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(QueueConsumer.class);

	private static final String CONSUMED = """
			INSERT IGNORE INTO consumed_message (consumer, message_id, event_type, consumed_at)
			VALUES (?, ?, ?, ?)
			""";

	private static final String ALREADY_CONSUMED = """
            SELECT COUNT(*) FROM consumed_message
            WHERE consumer = ? AND message_id = ?
            """;

    private static final int MAX_MESSAGES = 10;
    private static final int WORKER_VISIBILITY_SECONDS = 180;
    private static final int WORKER_RENEW_INTERVAL_SECONDS = 60;
    private static final Duration WORKER_SHUTDOWN_WAIT = Duration.ofMinutes(4);

	private static final Duration PAUSE_AFTER_FAILURE = Duration.ofSeconds(1);

	private final SqsClient sqs;

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transactions;

	private final JsonMapper json;

	private final Clock clock;

	private final String service;

	private final String queueUrl;

	private final Duration receiveWait;

    private final boolean longRunningWorker;
    private final Duration heartbeatInterval;

	private final Map<String, EventHandler<?>> handlers;

	private Thread worker;

	private volatile boolean running;

	QueueConsumer(SqsClient sqs, JdbcTemplate jdbc, TransactionTemplate transactions, JsonMapper json, Clock clock,
			String service, EventsProperties properties, List<EventHandler<?>> handlers) {
        this(sqs, jdbc, transactions, json, clock, service, properties, handlers,
                Duration.ofSeconds(WORKER_RENEW_INTERVAL_SECONDS));
    }

    // Package-private seam for deterministic heartbeat failure tests.
    QueueConsumer(SqsClient sqs, JdbcTemplate jdbc, TransactionTemplate transactions, JsonMapper json, Clock clock,
            String service, EventsProperties properties, List<EventHandler<?>> handlers, Duration heartbeatInterval) {
        if (heartbeatInterval.isNegative() || heartbeatInterval.isZero()) {
            throw new IllegalArgumentException("Heartbeat interval must be positive");
        }
        this.heartbeatInterval = heartbeatInterval;
		this.sqs = sqs;
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.json = json;
		this.clock = clock;
		this.service = service;
		this.queueUrl = properties.queueUrl();
		this.receiveWait = properties.receiveWait();
        this.longRunningWorker = "report-worker".equals(service);
		this.handlers = byType(handlers);
	}

	private static Map<String, EventHandler<?>> byType(List<EventHandler<?>> handlers) {
		Map<String, EventHandler<?>> byType = new HashMap<>();
		for (EventHandler<?> handler : handlers) {
			EventHandler<?> other = byType.putIfAbsent(handler.type(), handler);
			if (other != null) {
				throw new IllegalStateException("Two handlers for " + handler.type() + " events: "
						+ other.getClass().getName() + " and " + handler.getClass().getName());
			}
		}
		return Map.copyOf(byType);
	}

	@Override
	public void start() {
		running = true;
		worker = Thread.ofPlatform().name("event-consumer").daemon(true).start(this::receiveUntilStopped);
	}

	@Override
	public void stop() {
		running = false;
		if (worker == null) {
			return;
		}
		try {
            // Do not interrupt an in-flight Bedrock call or database commit.
            // The heartbeat continues until the message handler finishes.
            Duration wait = longRunningWorker ? WORKER_SHUTDOWN_WAIT : receiveWait.plusSeconds(5);
            worker.join(wait.toMillis());
            if (worker.isAlive()) {
                log.warn("Event consumer has not finished within {}; deployment termination may cause redelivery", wait);
            }
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public void destroy() {
        if (worker != null && worker.isAlive()) {
            // Do not close the client while the active handler may still renew visibility.
            log.warn("SQS client left open because the event consumer is still processing a message");
            return;
        }
		sqs.close();
	}

	private void receiveUntilStopped() {
		while (running) {
			try {
                for (Message message : receive()) {
                    if (!running) {
                        break;
                    }
                    handle(message);
				}
			}
			catch (RuntimeException failed) {
				if (running) {
					log.warn("Receiving events failed; trying again in {}", PAUSE_AFTER_FAILURE, failed);
					pause();
				}
			}
		}
	}

	private List<Message> receive() {
		return sqs.receiveMessage(ReceiveMessageRequest.builder()
				.queueUrl(queueUrl)
				.maxNumberOfMessages(longRunningWorker ? 1 : MAX_MESSAGES)
				.waitTimeSeconds((int) receiveWait.toSeconds())
				.messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
				.build()).messages();
	}

	void handle(Message message) {
		Envelope event;
		try {
			event = Envelope.read(message.body(), json);
		}
		catch (RuntimeException unreadable) {
			log.error("Message {} is not an event; it stays on the queue until SQS moves it to the dead-letter queue",
					message.messageId(), unreadable);
			return;
		}
		EventHandler<?> handler = handlers.get(event.type());
		if (handler == null) {
			log.info("No handler for {} events; event {} ignored", event.type(), event.id());
			delete(message);
			return;
		}
        // Only the report worker needs a lease during slow external AI calls.
        // Normal event consumers retain their original transactional behavior.
        try (VisibilityHeartbeat heartbeat =
                     longRunningWorker && handler instanceof PreparedEventHandler<?>
                             ? new VisibilityHeartbeat(sqs, queueUrl, message,
                                      heartbeatInterval, WORKER_VISIBILITY_SECONDS) : null) {
            if (handler instanceof PreparedEventHandler<?> prepared) {
                if (alreadyConsumed(event)) {
                    log.info("{} event {} was handled before; this delivery is ignored", event.type(), event.id());
                } else {
                    Runnable commit = prepare(prepared, event);
                    if (heartbeat != null) {
                        heartbeat.checkHealthy();
                    }
                    transactions.executeWithoutResult(status -> {
                        if (heartbeat != null) {
                            heartbeat.checkHealthy();
                        }
                        if (firstDelivery(event)) {
                            commit.run();
                        } else {
                            log.info("{} event {} was handled before; this delivery is ignored", event.type(), event.id());
                        }
                    });
                }
            } else {
                transactions.executeWithoutResult(status -> {
                    if (firstDelivery(event)) {
                        dispatch(handler, event);
                    } else {
                        log.info("{} event {} was handled before; this delivery is ignored", event.type(), event.id());
                    }
                });
            }
        } catch (RuntimeException failed) {
            log.error("Handling {} event {} failed on receive {}; SQS delivers it again", event.type(), event.id(),
                    message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT), failed);
            return;
        }

		delete(message);
	}

    /** Optimization only; never replaces the atomic insert in firstDelivery. */
    private boolean alreadyConsumed(Envelope event) {
        Integer count = jdbc.queryForObject(ALREADY_CONSUMED, Integer.class, service, event.id());
        return count != null && count > 0;
    }

	/** Records the event as handled; false when a row was already there. */
	private boolean firstDelivery(Envelope event) {
		return jdbc.update(CONSUMED, service, event.id(), event.type(), Timestamp.from(clock.instant())) == 1;
	}

	private <T> void dispatch(EventHandler<T> handler, Envelope event) {
		T payload = json.treeToValue(event.payload(), handler.payloadType());
		handler.handle(payload, event.metadata());
	}

    private <T> Runnable prepare(PreparedEventHandler<T> handler, Envelope event) {
        T payload = json.treeToValue(event.payload(), handler.payloadType());
        return handler.prepare(payload, event.metadata());
    }


	private void delete(Message message) {
		sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(message.receiptHandle()).build());
	}

	private static void pause() {
		try {
			Thread.sleep(PAUSE_AFTER_FAILURE);
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

}
