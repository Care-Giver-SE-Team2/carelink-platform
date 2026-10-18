
package sg.nus.carelink.report.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.core.env.Environment;

import sg.nus.carelink.events.EventHandler;
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

        when(environment.getActiveProfiles())
                .thenReturn(new String[] {"worker"});
    }

    @Test
    void exposesEventContract() {
        EventHandler<Map> handler = createHandler();

        assertThat(handler.type())
                .isEqualTo("ReportRequested");

        assertThat(handler.payloadType())
                .isEqualTo(Map.class);

        assertThat(handler)
                .isInstanceOf(PreparedEventHandler.class);
    }

    @Test
    void generatesPreviousWeekFromScheduledTime() {
        PreparedEventHandler<Map> handler =
                preparedHandler();

        EventMetadata metadata = metadata(
                "2026-10-19T02:00:00Z");

        Runnable commit = handler.prepare(
                Map.of(),
                metadata);

        LocalDate start =
                LocalDate.of(2026, 10, 12);

        LocalDate end =
                LocalDate.of(2026, 10, 18);

        // Preparation must happen before persistence.
        verify(reports).prepareBatch(
                null,
                start,
                end,
                null);

        verify(reports, never())
                .commitPreparedBatch(
                        org.mockito.ArgumentMatchers.any());

        // The consumer runs this inside its write transaction.
        commit.run();

        InOrder order = inOrder(reports);

        order.verify(reports).prepareBatch(
                null,
                start,
                end,
                null);

        order.verify(reports)
                .commitPreparedBatch(null);
    }

    @Test
    void rejectsMissingOccurredAtWithoutGeneratingReport() {
        PreparedEventHandler<Map> handler =
                preparedHandler();

        EventMetadata metadata =
                mock(EventMetadata.class);

        when(metadata.occurredAt())
                .thenReturn(null);

        assertThatThrownBy(() ->
                handler.prepare(Map.of(), metadata))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "ReportRequested requires occurredAt");

        verify(reports, never()).prepareBatch(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generatesPreviousYearWeekAcrossNewYearBoundary() {
        PreparedEventHandler<Map> handler =
                preparedHandler();

        EventMetadata metadata = metadata(
                "2027-01-04T02:00:00Z");

        Runnable commit = handler.prepare(
                Map.of(),
                metadata);

        LocalDate start =
                LocalDate.of(2026, 12, 28);

        LocalDate end =
                LocalDate.of(2027, 1, 3);

        verify(reports).prepareBatch(
                null,
                start,
                end,
                null);

        commit.run();

        InOrder order = inOrder(reports);

        order.verify(reports).prepareBatch(
                null,
                start,
                end,
                null);

        order.verify(reports)
                .commitPreparedBatch(null);
    }

    @Test
    void normalWorkerIgnoresConfiguredFaultDrillDelay() {
        when(environment.getActiveProfiles())
                .thenReturn(new String[] {"worker"});

        // A long configured delay must be ignored without
        // the fault-drill profile.
        EventHandler<Map> handler =
                configuration.reportRequestedEventHandler(
                        reports,
                        Duration.ofSeconds(90),
                        environment);

        assertThat(handler)
                .isInstanceOf(PreparedEventHandler.class);

        @SuppressWarnings("unchecked")
        PreparedEventHandler<Map> prepared =
                (PreparedEventHandler<Map>) handler;

        Runnable commit = prepared.prepare(
                Map.of(),
                metadata("2026-10-19T02:00:00Z"));

        verify(reports).prepareBatch(
                null,
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 18),
                null);

        commit.run();

        verify(reports).commitPreparedBatch(null);
    }

    @Test
    void rejectsNegativePreparationDelay() {
        assertThatThrownBy(() ->
                configuration.reportRequestedEventHandler(
                        reports,
                        Duration.ofSeconds(-1),
                        environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "must not be negative");
    }

    @Test
    void faultDrillProfileWithZeroDelayStillProcessesReports() {
        when(environment.getActiveProfiles())
                .thenReturn(
                        new String[] {"worker", "fault-drill"});

        EventHandler<Map> handler =
                configuration.reportRequestedEventHandler(
                        reports,
                        Duration.ZERO,
                        environment);

        @SuppressWarnings("unchecked")
        PreparedEventHandler<Map> prepared =
                (PreparedEventHandler<Map>) handler;

        Runnable commit = prepared.prepare(
                Map.of(),
                metadata("2026-10-19T02:00:00Z"));

        verify(reports).prepareBatch(
                null,
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 18),
                null);

        commit.run();

        verify(reports).commitPreparedBatch(null);
    }

    private EventHandler<Map> createHandler() {
        return configuration.reportRequestedEventHandler(
                reports,
                Duration.ZERO,
                environment);
    }

    @SuppressWarnings("unchecked")
    private PreparedEventHandler<Map> preparedHandler() {
        return (PreparedEventHandler<Map>) createHandler();
    }

    private static EventMetadata metadata(String occurredAt) {
        EventMetadata metadata =
                mock(EventMetadata.class);

        when(metadata.occurredAt())
                .thenReturn(Instant.parse(occurredAt));

        return metadata;
    }
}
