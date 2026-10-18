package sg.nus.carelink.events;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SqsException;


class VisibilityHeartbeatTest {
    private static final String QUEUE = "http://localhost:4566/000000000000/carelink-report-worker";

    private static Message message() {
        return Message.builder().messageId("message-1").receiptHandle("receipt-1").build();
    }

    @Test
    void renewsVisibilityWithCurrentReceiptWhileProcessing() throws Exception {
        SqsClient sqs = mock(SqsClient.class);
        CountDownLatch renewed = new CountDownLatch(1);
        when(sqs.changeMessageVisibility(any(ChangeMessageVisibilityRequest.class)))
                .thenAnswer(invocation -> {
                    renewed.countDown();
                    return ChangeMessageVisibilityResponse.builder().build();
                });

        try (VisibilityHeartbeat heartbeat = new VisibilityHeartbeat(sqs, QUEUE, message(),
                Duration.ofMillis(20), 180)) {
            assertThat(renewed.await(2, TimeUnit.SECONDS)).isTrue();
            heartbeat.checkHealthy();
        }

        ArgumentCaptor<ChangeMessageVisibilityRequest> request =
                ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
        verify(sqs, atLeastOnce()).changeMessageVisibility(request.capture());
        assertThat(request.getValue().queueUrl()).isEqualTo(QUEUE);
        assertThat(request.getValue().receiptHandle()).isEqualTo("receipt-1");
        assertThat(request.getValue().visibilityTimeout()).isEqualTo(180);
    }

    @Test
    void failedRenewalIsReportedToConsumer() throws Exception {
        SqsClient sqs = mock(SqsClient.class);
        CountDownLatch failed = new CountDownLatch(1);
        when(sqs.changeMessageVisibility(any(ChangeMessageVisibilityRequest.class)))
                .thenAnswer(invocation -> {
                    failed.countDown();
                    throw SqsException.builder().message("injected renewal failure").build();
                });

        try (VisibilityHeartbeat heartbeat = new VisibilityHeartbeat(sqs, QUEUE, message(),
                Duration.ofMillis(20), 180)) {
            assertThat(failed.await(2, TimeUnit.SECONDS)).isTrue();
            // The scheduler may signal the latch just before it publishes the failure.
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThatThrownBy(heartbeat::checkHealthy)
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("renewal failed"));
        }
    }

    @Test
    void closingHeartbeatStopsFurtherRenewals() throws Exception {
        SqsClient sqs = mock(SqsClient.class);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch renewed = new CountDownLatch(1);
        when(sqs.changeMessageVisibility(any(ChangeMessageVisibilityRequest.class)))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    renewed.countDown();
                    return ChangeMessageVisibilityResponse.builder().build();
                });

        VisibilityHeartbeat heartbeat = new VisibilityHeartbeat(sqs, QUEUE, message(),
                Duration.ofMillis(20), 180);
        try {
            assertThat(renewed.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            heartbeat.close();
        }
        int afterClose = calls.get();
        Thread.sleep(100);
        assertThat(calls.get()).isEqualTo(afterClose);
    }
}
