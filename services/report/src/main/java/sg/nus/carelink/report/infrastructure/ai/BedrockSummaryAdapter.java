package sg.nus.carelink.report.infrastructure.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

/** Bounded retries and a three-state circuit breaker. Never logs prompts or responses. */
public final class BedrockSummaryAdapter implements ReportAiSummary {
    private static final Logger log = LoggerFactory.getLogger(BedrockSummaryAdapter.class);
    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final BedrockRuntimeClient client;
    private final String modelId;
    private final Clock clock;
    private final int maxAttempts;
    private final int failureThreshold;
    private final Duration openDuration;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openUntil = Instant.MIN;
    private boolean probeInFlight;
    private long generation;

    public BedrockSummaryAdapter(BedrockRuntimeClient client, String modelId, Clock clock,
            int maxAttempts, int failureThreshold, Duration openDuration) {
        this.client = java.util.Objects.requireNonNull(client);
        this.modelId = java.util.Objects.requireNonNull(modelId);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.maxAttempts = Math.max(1, Math.min(3, maxAttempts));
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration = java.util.Objects.requireNonNull(openDuration);
        if (openDuration.isNegative() || openDuration.isZero()) {
            throw new IllegalArgumentException("openDuration must be positive");
        }
    }

    @Override
    public Optional<String> summarize(String metrics) {
        if (metrics == null || metrics.isBlank()) return Optional.empty();

        boolean probe;
        long admissionGeneration;
        synchronized (this) {
            if (state == State.OPEN) {
                if (clock.instant().isBefore(openUntil)) return Optional.empty();
                state = State.HALF_OPEN;
                probeInFlight = false;
            }
            if (state == State.HALF_OPEN) {
                if (probeInFlight) return Optional.empty();
                probeInFlight = true;
                probe = true;
            } else {
                probe = false; // CLOSED permits concurrent calls.
            }
            admissionGeneration = generation;
        }

        boolean success = false;
        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    var response = client.converse(ConverseRequest.builder()
                            .modelId(modelId)
                            .system(SystemContentBlock.builder().text(
                                    "Summarize aggregate care-service metrics in plain English. "
                                    + "Do not add numbers, names, diagnoses, medical advice or unsupported facts. "
                                    + "Maximum 90 words.").build())
                            .messages(Message.builder().role(ConversationRole.USER)
                                    .content(ContentBlock.fromText(metrics)).build())
                            .inferenceConfig(InferenceConfiguration.builder()
                                    .maxTokens(160).temperature(0.0f).build())
                            .build());
                    String text = response.output().message().content().stream()
                            .map(ContentBlock::text)
                            .filter(java.util.Objects::nonNull)
                            .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b).strip();
                    if (text.isBlank() || text.length() > 1200) {
                        throw new IllegalStateException("Invalid model response");
                    }
                    success = true;
                    return Optional.of(text);
                } catch (RuntimeException failure) {
                    boolean retryable = failure instanceof ThrottlingException
                            || failure instanceof SdkClientException
                            || (failure instanceof BedrockRuntimeException e && e.statusCode() >= 500);
                    if (!retryable || attempt == maxAttempts) {
                        log.warn("Bedrock summary unavailable; template fallback ({})",
                                failure.getClass().getSimpleName());
                        return Optional.empty();
                    }
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return Optional.empty();
                    }
                }
            }
            return Optional.empty();
        } finally {
            synchronized (this) {
                // Ignore stale completions from calls admitted before a state transition.
                if (generation == admissionGeneration) {
                    if (success) {
                        consecutiveFailures = 0;
                        if (state != State.CLOSED) {
                            state = State.CLOSED;
                            generation++;
                        }
                    } else if (probe || state == State.CLOSED) {
                        if (probe || ++consecutiveFailures >= failureThreshold) {
                            state = State.OPEN;
                            openUntil = clock.instant().plus(openDuration);
                            generation++;
                        }
                    }
                }
                if (probe) probeInFlight = false;
            }
        }
    }
}
