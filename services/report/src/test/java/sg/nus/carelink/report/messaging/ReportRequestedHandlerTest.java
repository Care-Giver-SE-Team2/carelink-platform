package sg.nus.carelink.report.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import sg.nus.carelink.events.EventMetadata;
import sg.nus.carelink.events.PreparedEventHandler;
import sg.nus.carelink.report.application.ReportService;

class ReportRequestedHandlerTest {

    @Test
    void generatesPreviousWeekFromScheduledTime() {
        ReportService reports = mock(ReportService.class);
        var handler = preparedHandler(reports);
        // 2026-10-19 02:00 Singapore time; worker may process it days later.
        var metadata = new EventMetadata("scheduled-2026-10-19", "ReportRequested", "eventbridge",
                Instant.parse("2026-10-18T18:00:00Z"), null);

        Runnable commit = handler.prepare(Map.of(), metadata);
        InOrder calls = inOrder(reports);
        calls.verify(reports).prepareBatch(null, LocalDate.parse("2026-10-12"),
                LocalDate.parse("2026-10-18"), null);
        calls.verifyNoMoreInteractions();

        commit.run();
        // Mockito returns null for an unstubbed PreparedBatch; production returns a real batch.
        calls.verify(reports).commitPreparedBatch(null);
        calls.verifyNoMoreInteractions();
    }

    @Test
    void exposesEventContract() {
        var handler = new ReportRequestedHandler().reportRequestedEventHandler(mock(ReportService.class));

        assertEquals("ReportRequested", handler.type());
        assertEquals(Map.class, handler.payloadType());
        assertInstanceOf(PreparedEventHandler.class, handler);
    }

    @Test
    void rejectsMissingOccurredAtWithoutGeneratingReport() {
        ReportService reports = mock(ReportService.class);
        var handler = preparedHandler(reports);
        var metadata = new EventMetadata("missing-time", "ReportRequested", "test", null, null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> handler.prepare(Map.of(), metadata));

        assertEquals("ReportRequested requires occurredAt", error.getMessage());
        verifyNoInteractions(reports);
    }

    @Test
    void generatesPreviousYearWeekAcrossNewYearBoundary() {
        ReportService reports = mock(ReportService.class);
        var handler = preparedHandler(reports);
        // 2027-01-04 02:00 Singapore time.
        var metadata = new EventMetadata("scheduled-2027-01-04", "ReportRequested", "eventbridge",
                Instant.parse("2027-01-03T18:00:00Z"), null);

        Runnable commit = handler.prepare(Map.of(), metadata);
        InOrder calls = inOrder(reports);
        calls.verify(reports).prepareBatch(null, LocalDate.parse("2026-12-28"),
                LocalDate.parse("2027-01-03"), null);
        calls.verifyNoMoreInteractions();

        commit.run();
        calls.verify(reports).commitPreparedBatch(null);
        calls.verifyNoMoreInteractions();
    }

    @SuppressWarnings("unchecked")
    private static PreparedEventHandler<Map> preparedHandler(ReportService reports) {
        return assertInstanceOf(PreparedEventHandler.class,
                new ReportRequestedHandler().reportRequestedEventHandler(reports));
    }
}
