package sg.nus.carelink.report.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import sg.nus.carelink.events.EventMetadata;
import sg.nus.carelink.events.PreparedEventHandler;
import sg.nus.carelink.report.application.ReportService;

class ReportRequestedHandlerTest {
    private ReportService reports;
    private Environment environment;
    private ReportRequestedHandler configuration;

    @BeforeEach
    void setUp() {
        reports = mock(ReportService.class);
        environment = mock(Environment.class);
        configuration = new ReportRequestedHandler();
        when(environment.getActiveProfiles()).thenReturn(new String[] {"worker"});
    }

    @SuppressWarnings("unchecked")
    private PreparedEventHandler<Map> handler() {
        return (PreparedEventHandler<Map>) configuration.reportRequestedEventHandler(
                reports, Duration.ZERO, environment);
    }

    private static EventMetadata metadata() {
        EventMetadata metadata = mock(EventMetadata.class);
        when(metadata.occurredAt()).thenReturn(Instant.parse("2026-10-18T09:00:00Z"));
        return metadata;
    }

    private static Map<String, Object> valid(long elderId) {
        return Map.of("schemaVersion", 1, "elderId", elderId,
                "periodStart", "2026-10-05", "periodEnd", "2026-10-11",
                "runId", "cl-keda-fixture-01");
    }

    @Test
    void preparesOnlyRequestedElderAndCommitsLater() {
        Runnable commit = handler().prepare(valid(700000101L), metadata());
        verify(reports).prepareBatch(700000101L, LocalDate.of(2026,10,5),
                LocalDate.of(2026,10,11), null);
        verify(reports, never()).commitPreparedBatch(any());
        commit.run();
        verify(reports).commitPreparedBatch(any());
    }

    @Test
    void independentEldersProduceIndependentPreparations() {
        handler().prepare(valid(700000101L), metadata());
        handler().prepare(valid(700000102L), metadata());
        verify(reports).prepareBatch(eq(700000101L), any(), any(), isNull());
        verify(reports).prepareBatch(eq(700000102L), any(), any(), isNull());
    }

    @Test
    void rejectsInvalidPayloadsBeforePreparing() {
        Object[][] invalid = {
            {"elderId", null}, {"elderId", 0}, {"elderId", -1},
            {"elderId", true}, {"schemaVersion", 2},
            {"periodStart", "2026-10-06"}, {"periodEnd", "2026-10-12"},
            {"runId", "INVALID_ID"}
        };
        for (Object[] item : invalid) {
            Map<String, Object> payload = new HashMap<>(valid(700000101L));
            if (item[1] == null) payload.remove(item[0]);
            else payload.put((String)item[0], item[1]);
            assertThatThrownBy(() -> handler().prepare(payload, metadata()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(reports);
    }

    @Test
    void rejectsUnknownFieldsAndIncompleteWeek() {
        Map<String, Object> payload = new HashMap<>(valid(700000101L));
        payload.put("extra", "unexpected");
        assertThatThrownBy(() -> handler().prepare(payload, metadata()))
                .isInstanceOf(IllegalArgumentException.class);
        payload.remove("extra");
        payload.put("periodStart", "2026-10-12");
        payload.put("periodEnd", "2026-10-18");
        assertThatThrownBy(() -> handler().prepare(payload, metadata()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(reports);
    }

    @Test
    void normalWorkerIgnoresFaultDrillDelay() {
        @SuppressWarnings("unchecked")
        PreparedEventHandler<Map> handler = (PreparedEventHandler<Map>)
                configuration.reportRequestedEventHandler(reports, Duration.ofSeconds(90), environment);
        handler.prepare(valid(700000101L), metadata());
        verify(reports).prepareBatch(eq(700000101L), any(), any(), isNull());
    }

    @Test
    void rejectsNegativeDelay() {
        assertThatThrownBy(() -> configuration.reportRequestedEventHandler(
                reports, Duration.ofSeconds(-1), environment))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
