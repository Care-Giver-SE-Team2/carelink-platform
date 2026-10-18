package sg.nus.carelink.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;
import tools.jackson.databind.json.JsonMapper;

class QueueConsumerLifecycleTest {
    private static final String BODY = """
            {"id":"heartbeat-worker-test","type":"ReportRequested","source":"test",
             "occurredAt":"2026-10-19T00:00:00Z","payload":{}}
            """;

    record Request() {}

    private static Message message() {
        return Message.builder().messageId("sqs-test").receiptHandle("receipt-test")
                .body(BODY)
                .attributes(Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "1"))
                .build();
    }

    private static EventsProperties properties() {
        return new EventsProperties("report-worker", null, "http://localhost/queue", null,
                "ap-southeast-1", Duration.ofSeconds(1), 10, Duration.ofSeconds(1));
    }

    @Test
    void failedHeartbeatDuringPreparePreventsCommitAndAcknowledgement() throws Exception {
        SqsClient sqs = mock(SqsClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        CountDownLatch prepareStarted = new CountDownLatch(1);
        CountDownLatch releasePrepare = new CountDownLatch(1);
        AtomicInteger commits = new AtomicInteger();

        when(jdbc.queryForObject(any(String.class), eq(Integer.class),
                eq("report-worker"), eq("heartbeat-worker-test"))).thenReturn(0);
        when(sqs.changeMessageVisibility(any(ChangeMessageVisibilityRequest.class)))
                .thenThrow(SqsException.builder().message("simulated renewal failure").build());

        PreparedEventHandler<Request> handler = new PreparedEventHandler<>() {
            @Override public String type() { return "ReportRequested"; }
            @Override public Class<Request> payloadType() { return Request.class; }
            @Override public Runnable prepare(Request request, EventMetadata metadata) {
                prepareStarted.countDown();
                try {
                    if (!releasePrepare.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("prepare was not released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return commits::incrementAndGet;
            }
        };
        QueueConsumer consumer = new QueueConsumer(sqs, jdbc, transactions,
                new JsonMapper(), Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC),
                "report-worker", properties(), List.of(handler), Duration.ofMillis(15));

        Thread processing = Thread.ofPlatform().start(() -> consumer.handle(message()));
        try {
            assertThat(prepareStarted.await(2, TimeUnit.SECONDS)).isTrue();
            verify(sqs, timeout(2000)).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
        } finally {
            releasePrepare.countDown();
            processing.join(3000);
        }
        assertThat(processing.isAlive()).isFalse();
        assertThat(commits.get()).isZero();
        verify(transactions, never()).executeWithoutResult(any());
        verify(sqs, never()).deleteMessage(any(software.amazon.awssdk.services.sqs.model.DeleteMessageRequest.class));
    }

    @Test
    void stopWaitsForInFlightPrepareAndDoesNotReceiveAgain() throws Exception {
        SqsClient sqs = mock(SqsClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        CountDownLatch prepareStarted = new CountDownLatch(1);
        CountDownLatch releasePrepare = new CountDownLatch(1);
        AtomicInteger commits = new AtomicInteger();

        when(sqs.receiveMessage(any(software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message()).build())
                .thenReturn(ReceiveMessageResponse.builder().build());
        when(jdbc.queryForObject(any(String.class), eq(Integer.class),
                eq("report-worker"), eq("heartbeat-worker-test"))).thenReturn(0);
        when(jdbc.update(any(String.class), eq("report-worker"), eq("heartbeat-worker-test"),
                eq("ReportRequested"), any(java.sql.Timestamp.class))).thenReturn(1);
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(org.springframework.transaction.TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());

        PreparedEventHandler<Request> handler = new PreparedEventHandler<>() {
            @Override public String type() { return "ReportRequested"; }
            @Override public Class<Request> payloadType() { return Request.class; }
            @Override public Runnable prepare(Request request, EventMetadata metadata) {
                prepareStarted.countDown();
                try {
                    if (!releasePrepare.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("prepare was not released");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return commits::incrementAndGet;
            }
        };
        QueueConsumer consumer = new QueueConsumer(sqs, jdbc, transactions,
                new JsonMapper(), Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC),
                "report-worker", properties(), List.of(handler), Duration.ofMillis(30));

        consumer.start();
        Thread stopping = null;
        try {
            assertThat(prepareStarted.await(2, TimeUnit.SECONDS)).isTrue();
            stopping = Thread.ofPlatform().start(consumer::stop);
            // stop must wait for the in-flight handler, not return immediately.
            Thread.sleep(70);
            assertThat(stopping.isAlive()).isTrue();
        } finally {
            releasePrepare.countDown();
            if (stopping != null) stopping.join(3000);
            consumer.stop();
            consumer.destroy();
        }
        assertThat(stopping).isNotNull();
        assertThat(stopping.isAlive()).isFalse();
        assertThat(commits.get()).isEqualTo(1);
        verify(sqs, times(1)).receiveMessage(any(software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest.class));
        verify(sqs).deleteMessage(any(software.amazon.awssdk.services.sqs.model.DeleteMessageRequest.class));
    }
}
