package sg.nus.carelink.report.infrastructure.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

class BedrockSummaryAdapterTest {
    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-10-16T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
    }

    private static ConverseResponse answer(String text) {
        return ConverseResponse.builder().output(ConverseOutput.builder()
                .message(Message.builder().content(ContentBlock.fromText(text)).build()).build()).build();
    }

    private static BedrockSummaryAdapter adapter(BedrockRuntimeClient client, Clock clock, int attempts, int threshold) {
        return new BedrockSummaryAdapter(client, "test-model", clock, attempts, threshold, Duration.ofSeconds(60));
    }

    @Test void successfulResponse() {
        var client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(answer("Two visits completed."));
        assertEquals(Optional.of("Two visits completed."), adapter(client, Clock.systemUTC(), 1, 2)
                .summarize("visits: 2"));
    }

    @Test void opensCircuitAndFallsBackWithoutFurtherCalls() {
        var client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenThrow(new IllegalArgumentException("invalid"));
        var breaker = adapter(client, Clock.systemUTC(), 1, 2);
        assertTrue(breaker.summarize("visits: 2").isEmpty());
        assertTrue(breaker.summarize("visits: 2").isEmpty());
        assertTrue(breaker.summarize("visits: 2").isEmpty());
        verify(client, times(2)).converse(any(ConverseRequest.class));
    }

    @Test void halfOpenSuccessClosesCircuit() {
        var clock = new MutableClock();
        var client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class)))
                .thenThrow(new IllegalArgumentException("down"))
                .thenReturn(answer("Recovered"), answer("Still healthy"));
        var breaker = adapter(client, clock, 1, 1);
        assertTrue(breaker.summarize("visits: 2").isEmpty());
        assertTrue(breaker.summarize("visits: 2").isEmpty());
        clock.advance(Duration.ofSeconds(61));
        assertEquals(Optional.of("Recovered"), breaker.summarize("visits: 2"));
        assertEquals(Optional.of("Still healthy"), breaker.summarize("visits: 2"));
        verify(client, times(3)).converse(any(ConverseRequest.class));
    }

    @Test void halfOpenFailureReopensCircuit() {
        var clock = new MutableClock();
        var client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenThrow(new IllegalArgumentException("down"));
        var breaker = adapter(client, clock, 1, 1);
        assertTrue(breaker.summarize("visits: 2").isEmpty());
        clock.advance(Duration.ofSeconds(61));
        assertTrue(breaker.summarize("visits: 2").isEmpty());
        assertTrue(breaker.summarize("visits: 2").isEmpty());
        verify(client, times(2)).converse(any(ConverseRequest.class));
    }

    @Test void retriesTransientFailureThenSucceeds() {
        var client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class)))
                .thenThrow(SdkClientException.builder().message("temporary").build())
                .thenReturn(answer("Recovered"));
        assertEquals(Optional.of("Recovered"), adapter(client, Clock.systemUTC(), 2, 2)
                .summarize("visits: 2"));
        verify(client, times(2)).converse(any(ConverseRequest.class));
    }

    @Test void closedAllowsConcurrentCalls() throws Exception {
        var client = mock(BedrockRuntimeClient.class);
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        when(client.converse(any(ConverseRequest.class))).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
            return answer("Success");
        });
        var breaker = adapter(client, Clock.systemUTC(), 1, 2);
        var first = new AtomicReference<Optional<String>>();
        var second = new AtomicReference<Optional<String>>();
        Thread firstThread = new Thread(() -> first.set(breaker.summarize("visits: 2")));
        Thread secondThread = new Thread(() -> second.set(breaker.summarize("visits: 2")));
        firstThread.start();
        secondThread.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS), "Both calls should reach Bedrock concurrently");
        } finally {
            release.countDown();
            firstThread.join(5000);
            secondThread.join(5000);
        }
        assertEquals(Optional.of("Success"), first.get());
        assertEquals(Optional.of("Success"), second.get());
        verify(client, times(2)).converse(any(ConverseRequest.class));
    }

    @Test void halfOpenAllowsOnlyOneProbe() throws Exception {
        var clock = new MutableClock();
        var client = mock(BedrockRuntimeClient.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(client.converse(any(ConverseRequest.class)))
                .thenThrow(new IllegalArgumentException("down"))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                    return answer("Recovered");
                });
        var breaker = adapter(client, clock, 1, 1);
        assertTrue(breaker.summarize("visits: 2").isEmpty());
        clock.advance(Duration.ofSeconds(61));
        var result = new AtomicReference<Optional<String>>();
        Thread probe = new Thread(() -> result.set(breaker.summarize("visits: 2")));
        probe.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(breaker.summarize("visits: 2").isEmpty());
        } finally {
            release.countDown();
            probe.join(5000);
        }
        assertEquals(Optional.of("Recovered"), result.get());
        verify(client, times(2)).converse(any(ConverseRequest.class));
    }
}
