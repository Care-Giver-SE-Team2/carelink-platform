package sg.nus.carelink.events;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Renews the visibility of one SQS receipt while a long-running handler is active.
 * This is a best-effort lease extension, not a distributed lock.
 */
final class VisibilityHeartbeat implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(VisibilityHeartbeat.class);

    private final ScheduledExecutorService executor;
    private final ScheduledFuture<?> task;
    private final AtomicReference<RuntimeException> failure = new AtomicReference<>();

    VisibilityHeartbeat(SqsClient sqs, String queueUrl, Message message,
            Duration interval, int visibilitySeconds) {
        Objects.requireNonNull(sqs, "sqs");
        Objects.requireNonNull(queueUrl, "queueUrl");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(interval, "interval");
        if (interval.isZero() || interval.isNegative() || interval.toMillis() < 1) {
            throw new IllegalArgumentException("Heartbeat interval must be at least 1 ms");
        }
        if (visibilitySeconds < 1 || visibilitySeconds > 43200) {
            throw new IllegalArgumentException("SQS visibility timeout must be between 1 and 43200 seconds");
        }
        executor = Executors.newSingleThreadScheduledExecutor(r ->
                Thread.ofPlatform().daemon(true).name("report-visibility-heartbeat").unstarted(r));
        long intervalMillis = interval.toMillis();
        task = executor.scheduleAtFixedRate(() -> {
            try {
                sqs.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .visibilityTimeout(visibilitySeconds)
                        .build());
            } catch (RuntimeException error) {
                failure.compareAndSet(null, error);
                log.error("Cannot renew visibility for SQS message {}", message.messageId(), error);
                // No more renewals after the first failure. The caller must not commit if it observes it.
                throw error;
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    void checkHealthy() {
        RuntimeException error = failure.get();
        if (error != null) {
            throw new IllegalStateException("SQS visibility renewal failed", error);
        }
    }

    @Override
    public void close() {
        task.cancel(false);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
