package sg.nus.carelink.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.SqsException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deterministic failure injection at the SQS acknowledgement boundary.
 * No Docker required. The same event body is delivered twice with different
 * receipt handles, as happens after a visibility timeout.
 */
class QueueConsumerRedeliveryTest {

    private static final String EVENT_ID = "redelivery-001";
    private static final String EVENT_BODY = """
            {"id":"redelivery-001","type":"ReportRequested","source":"test",
             "occurredAt":"2026-10-19T00:00:00Z","payload":{}}
            """;

    record Request() {}

    @Test
    void committedMessageIsNotPreparedAgainWhenDeleteFailsAndSqsRedelivers() {
        SqsClient sqs = mock(SqsClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        AtomicInteger prepares = new AtomicInteger();
        AtomicInteger commits = new AtomicInteger();
        AtomicInteger stored = new AtomicInteger();

        PreparedEventHandler<Request> handler = new PreparedEventHandler<>() {
            @Override public String type() { return "ReportRequested"; }
            @Override public Class<Request> payloadType() { return Request.class; }
            @Override public Runnable prepare(Request request, EventMetadata metadata) {
                assertThat(metadata.id()).isEqualTo(EVENT_ID);
                prepares.incrementAndGet();
                return commits::incrementAndGet;
            }
        };

        // Simulate the DB's committed consumed_message marker.
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq("report-worker"), eq(EVENT_ID)))
                .thenAnswer(invocation -> stored.get());
        when(jdbc.update(any(String.class), eq("report-worker"), eq(EVENT_ID),
                eq("ReportRequested"), any(java.sql.Timestamp.class)))
                .thenAnswer(invocation -> stored.compareAndSet(0, 1) ? 1 : 0);
        doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(org.springframework.transaction.TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());

        when(sqs.deleteMessage(any(DeleteMessageRequest.class)))
                .thenThrow(SqsException.builder().message("injected DeleteMessage failure").build())
                .thenReturn(DeleteMessageResponse.builder().build());

        QueueConsumer consumer = new QueueConsumer(
                sqs, jdbc, transactions, new JsonMapper(),
                Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC),
                "report-worker",
                new EventsProperties("report-worker", null, "http://localhost/queue", null,
                        "ap-southeast-1", Duration.ofSeconds(1), 10, Duration.ofSeconds(1)),
                List.of(handler));

        // First delivery commits the DB marker and report, but SQS deletion fails.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.handle(message("receipt-1")))
                .isInstanceOf(SqsException.class);
        assertThat(stored.get()).isEqualTo(1);
        assertThat(prepares.get()).isEqualTo(1);
        assertThat(commits.get()).isEqualTo(1);

        // After the visibility timeout SQS supplies the same body with a new handle.
        consumer.handle(message("receipt-2"));
        assertThat(prepares.get()).as("duplicate must not call Bedrock").isEqualTo(1);
        assertThat(commits.get()).as("duplicate must not write reports").isEqualTo(1);
        verify(sqs, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }

    private static Message message(String receipt) {
        return Message.builder()
                .messageId("sqs-" + receipt)
                .receiptHandle(receipt)
                .body(EVENT_BODY)
                .attributes(Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "2"))
                .build();
    }
}
